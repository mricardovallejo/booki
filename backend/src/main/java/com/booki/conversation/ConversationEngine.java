package com.booki.conversation;

import com.booki.ai.AiProvider;
import com.booki.ai.AiProviderException;
import com.booki.ai.AiProviderRegistry;
import com.booki.ai.StreamingAiProvider;
import com.booki.conversation.capability.CapabilityInvocation;
import com.booki.conversation.capability.CapabilityRegistry;
import com.booki.conversation.capability.ConversationCapability;
import com.booki.domain.DocumentPage;
import com.booki.domain.Message;
import com.booki.domain.Session;
import com.booki.repository.DocumentPageRepository;
import com.booki.repository.MessageRepository;
import com.booki.repository.SessionRepository;
import com.booki.service.impl.SessionContextBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * The single orchestrator for every conversational turn in BooKI, whatever its
 * source — text chat, a quick-action button, or (later) transcribed voice.
 *
 * <p>It owns the pipeline that used to live inline in {@code SessionServiceImpl}:
 * <ol>
 *   <li>resolve and ownership-check the {@link Session};</li>
 *   <li>build the recent conversation window (most recent N, chronological);</li>
 *   <li>persist the user turn;</li>
 *   <li>assemble the system prompt via {@link SessionContextBuilder} plus the
 *       session's page-range text (size-capped);</li>
 *   <li>call the session's {@link AiProvider};</li>
 *   <li>persist BooKI's reply, or raise a controlled failure.</li>
 * </ol>
 *
 * <p>It knows nothing about REST/SSE/WebSocket: callers pass a
 * {@link ConversationRequest} and receive a {@link ConversationResult}.
 * Conversational capabilities (quiz, summary, explain) plug in here in Phase 2.
 */
@Service
public class ConversationEngine {

    private static final String UNAVAILABLE =
            "The reading assistant is temporarily unavailable. Please try again in a moment.";

    private final SessionRepository sessionRepository;
    private final MessageRepository messageRepository;
    private final DocumentPageRepository documentPageRepository;
    private final AiProviderRegistry aiProviderRegistry;
    private final SessionContextBuilder sessionContextBuilder;
    private final CapabilityRegistry capabilityRegistry;
    private final int historyWindow;
    private final int maxContextChars;

    public ConversationEngine(SessionRepository sessionRepository,
                              MessageRepository messageRepository,
                              DocumentPageRepository documentPageRepository,
                              AiProviderRegistry aiProviderRegistry,
                              SessionContextBuilder sessionContextBuilder,
                              CapabilityRegistry capabilityRegistry,
                              @Value("${booki.conversation.history-window:20}") int historyWindow,
                              @Value("${booki.conversation.max-context-chars:24000}") int maxContextChars) {
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
        this.documentPageRepository = documentPageRepository;
        this.aiProviderRegistry = aiProviderRegistry;
        this.sessionContextBuilder = sessionContextBuilder;
        this.capabilityRegistry = capabilityRegistry;
        this.historyWindow = Math.max(1, historyWindow);
        this.maxContextChars = Math.max(1000, maxContextChars);
    }

    public ConversationResult converse(ConversationRequest request) {
        Session session = sessionRepository.findByIdAndUserId(request.sessionId(), request.userId())
                .orElseThrow(() -> new NoSuchElementException("Session not found"));

        // Build the window from the history that exists BEFORE this turn, so the
        // new user message is sent once (as the prompt), not also echoed inside
        // the context. Fixes the old bug where limit(20) took the OLDEST 20.
        List<AiProvider.Message> history = recentHistory(session.getId());

        Message userMessage = persist(session, Message.Speaker.USER, request.inputType(), request.text());

        String pageContext = buildContextText(session);

        String answer;
        try {
            answer = generateAnswer(request, session, history, pageContext);
        } catch (AiProviderException e) {
            // The user's turn stays in history; we simply don't fabricate a reply.
            throw new ConversationFailedException(UNAVAILABLE, e);
        }

        Message botMessage = persist(session, Message.Speaker.BOOKI, Message.InputType.TEXT, answer);
        return new ConversationResult(userMessage, botMessage);
    }

    /**
     * Streaming counterpart of {@link #converse} — additive, not a replacement.
     * The plain-chat reply is streamed token-by-token; an explicit
     * {@code capabilityHint} runs its capability and emits the result as one
     * delta (capabilities aren't token-streamable). Model-driven capability
     * routing still works: output is withheld only while the reply could still
     * be a {@code {"capability":...}} directive, then flushed live once it
     * can't be. The bot message is persisted before {@code onComplete}; nothing
     * is persisted on {@code onError}.
     *
     * <p>No HTTP transport calls this yet — SSE/WebSocket arrive with a concrete
     * streaming requirement (ADR-010). Setup errors (session not found, unknown
     * hint) propagate as exceptions so the transport can answer 404/400 before
     * any streaming begins.
     */
    public void converseStreaming(ConversationRequest request, ConversationStream out) {
        Session session = sessionRepository.findByIdAndUserId(request.sessionId(), request.userId())
                .orElseThrow(() -> new NoSuchElementException("Session not found"));

        List<AiProvider.Message> history = recentHistory(session.getId());
        Message userMessage = persist(session, Message.Speaker.USER, request.inputType(), request.text());
        String pageContext = buildContextText(session);
        CapabilityInvocation invocation = new CapabilityInvocation(session, request.text(), history, pageContext);

        Optional<ConversationCapability> hinted = hintedCapability(request);
        if (hinted.isPresent()) {
            String answer;
            try {
                answer = hinted.get().execute(invocation);
            } catch (AiProviderException e) {
                out.onError(new ConversationFailedException(UNAVAILABLE, e));
                return;
            }
            Message bot = persist(session, Message.Speaker.BOOKI, Message.InputType.TEXT, answer);
            out.onDelta(answer);
            out.onComplete(new ConversationResult(userMessage, bot));
            return;
        }

        String systemPrompt = sessionContextBuilder.buildSystemPrompt(session, pageContext)
                + capabilityRegistry.routerInstructions();

        aiProviderRegistry.converseStreaming(session.getAiProvider(), systemPrompt, history, request.text(),
                new DirectiveGatingStream(out, invocation, userMessage, session));
    }

    /**
     * Bridges the AI-layer {@link StreamingAiProvider.TokenStream} to the
     * domain {@link ConversationStream}, holding back output until the reply is
     * known not to be a capability-routing directive.
     */
    private final class DirectiveGatingStream implements StreamingAiProvider.TokenStream {

        private final ConversationStream out;
        private final CapabilityInvocation invocation;
        private final Message userMessage;
        private final Session session;
        private final boolean gating = !capabilityRegistry.isEmpty();
        private final int maxDirective = capabilityRegistry.maxDirectiveLength();
        private final StringBuilder full = new StringBuilder();
        private boolean flushed;

        private DirectiveGatingStream(ConversationStream out, CapabilityInvocation invocation,
                                      Message userMessage, Session session) {
            this.out = out;
            this.invocation = invocation;
            this.userMessage = userMessage;
            this.session = session;
        }

        @Override
        public void onDelta(String text) {
            full.append(text);
            if (!gating || flushed) {
                out.onDelta(text);
                return;
            }
            String seen = full.toString().stripLeading();
            if (!seen.isEmpty() && (seen.charAt(0) != '{' || seen.length() > maxDirective)) {
                flushed = true;
                out.onDelta(full.toString());
            }
        }

        @Override
        public void onComplete(String fullText) {
            Optional<String> routed = gating ? capabilityRegistry.parseDirective(fullText) : Optional.empty();
            if (routed.isPresent()) {
                // A directive is short and starts with '{', so it was never flushed — nothing bogus was streamed.
                String answer;
                try {
                    answer = capabilityRegistry.find(routed.get()).orElseThrow().execute(invocation);
                } catch (AiProviderException e) {
                    out.onError(new ConversationFailedException(UNAVAILABLE, e));
                    return;
                }
                Message bot = persist(session, Message.Speaker.BOOKI, Message.InputType.TEXT, answer);
                out.onDelta(answer);
                out.onComplete(new ConversationResult(userMessage, bot));
                return;
            }
            if (gating && !flushed) {
                out.onDelta(fullText);
            }
            Message bot = persist(session, Message.Speaker.BOOKI, Message.InputType.TEXT, fullText);
            out.onComplete(new ConversationResult(userMessage, bot));
        }

        @Override
        public void onError(RuntimeException error) {
            out.onError(new ConversationFailedException(UNAVAILABLE, error));
        }
    }

    private Optional<ConversationCapability> hintedCapability(ConversationRequest request) {
        if (request.capabilityHint() == null || request.capabilityHint().isBlank()) {
            return Optional.empty();
        }
        return Optional.of(capabilityRegistry.find(request.capabilityHint())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown capability: " + request.capabilityHint())));
    }

    /**
     * Produces the reply text: either a plain model answer, or a conversational
     * capability's output when one applies.
     *
     * <ul>
     *   <li>An explicit {@code capabilityHint} (from a quick-action button) runs
     *       that capability directly — no routing model call.</li>
     *   <li>Otherwise the session's normal {@code converse()} call carries the
     *       router instructions; if the model replies with a capability
     *       directive, that capability runs, else its reply is the answer.</li>
     * </ul>
     */
    private String generateAnswer(ConversationRequest request, Session session,
                                  List<AiProvider.Message> history, String pageContext) {
        CapabilityInvocation invocation =
                new CapabilityInvocation(session, request.text(), history, pageContext);

        Optional<ConversationCapability> hinted = hintedCapability(request);
        if (hinted.isPresent()) {
            return hinted.get().execute(invocation);
        }

        String systemPrompt = sessionContextBuilder.buildSystemPrompt(session, pageContext)
                + capabilityRegistry.routerInstructions();
        String reply = aiProviderRegistry.get(session.getAiProvider())
                .converse(systemPrompt, history, request.text());

        return capabilityRegistry.parseDirective(reply)
                .flatMap(capabilityRegistry::find)
                .map(capability -> capability.execute(invocation))
                .orElse(reply);
    }

    /** Most recent {@code historyWindow} messages, returned in chronological order for the model. */
    private List<AiProvider.Message> recentHistory(Long sessionId) {
        List<Message> newestFirst = messageRepository.findBySessionIdOrderByCreatedAtDesc(
                sessionId, PageRequest.of(0, historyWindow));
        List<Message> chronological = new ArrayList<>(newestFirst);
        Collections.reverse(chronological);
        return chronological.stream()
                .map(m -> new AiProvider.Message(
                        m.getSpeaker() == Message.Speaker.USER ? "user" : "assistant",
                        m.getMessage()))
                .toList();
    }

    /**
     * The session's page-range text, capped at {@code maxContextChars} so an
     * extremely wide range cannot produce an unbounded LLM request. A fuller
     * context-selection strategy is a later concern; this is just the guard.
     */
    private String buildContextText(Session session) {
        List<DocumentPage> pages = documentPageRepository
                .findByDocumentIdAndPageNumberBetweenOrderByPageNumberAsc(
                        session.getDocument().getId(), session.getStartPage(), session.getEndPage());

        StringBuilder sb = new StringBuilder();
        for (DocumentPage page : pages) {
            String block = "[Page " + page.getPageNumber() + "]\n" + page.getExtractedText();
            if (sb.length() + block.length() > maxContextChars) {
                sb.append(sb.isEmpty() ? "" : "\n\n")
                        .append("[Context truncated to about ").append(maxContextChars)
                        .append(" characters of this session's page range.]");
                break;
            }
            if (!sb.isEmpty()) {
                sb.append("\n\n");
            }
            sb.append(block);
        }
        return sb.toString();
    }

    private Message persist(Session session, Message.Speaker speaker, Message.InputType inputType, String text) {
        Message message = new Message();
        message.setSession(session);
        message.setSpeaker(speaker);
        message.setInputType(inputType);
        message.setMessage(text);
        return messageRepository.save(message);
    }
}
