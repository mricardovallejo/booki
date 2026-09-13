package com.booki.conversation;

import com.booki.ai.ActivityContent;
import com.booki.ai.ActivityContentService;
import com.booki.ai.AiProvider;
import com.booki.ai.AiProviderException;
import com.booki.ai.AiProviderRegistry;
import com.booki.ai.StreamingAiProvider;
import com.booki.conversation.capability.CapabilityInvocation;
import com.booki.conversation.capability.CapabilityRegistry;
import com.booki.conversation.capability.ConversationCapability;
import com.booki.domain.Message;
import com.booki.domain.Session;
import com.booki.repository.MessageRepository;
import com.booki.repository.SessionRepository;
import com.booki.domain.Capability;
import com.booki.prompt.PromptAssembler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;

/**
 * The single orchestrator for every conversational turn in BooKI, whatever its
 * source — text chat, a quick-action button, or (later) transcribed voice.
 *
 * <p>It owns the pipeline that used to live inline in {@code SessionServiceImpl}:
 * <ol>
 *   <li>resolve and ownership-check the {@link Session};</li>
 *   <li>build the recent conversation window (most recent N, chronological);</li>
 *   <li>persist the user turn;</li>
 *   <li>assemble the system prompt via {@link PromptAssembler} plus the
 *       bounded recent-page text (size-capped), and the capability router
 *       filtered to the profile's enabled set;</li>
 *   <li>call the session's {@link AiProvider};</li>
 *   <li>persist BooKI's reply, or raise a controlled failure.</li>
 * </ol>
 *
 * <p>It knows nothing about REST/SSE/WebSocket: callers pass a
 * {@link ConversationRequest} and receive a {@link ConversationResult}.
 */
@Slf4j
@Service
public class ConversationEngine {

    private static final String UNAVAILABLE =
            "The reading assistant is temporarily unavailable. Please try again in a moment.";

    private final SessionRepository sessionRepository;
    private final MessageRepository messageRepository;
    private final ActivityContentService activityContentService;
    private final AiProviderRegistry aiProviderRegistry;
    private final PromptAssembler promptAssembler;
    private final CapabilityRegistry capabilityRegistry;
    private final int historyWindow;

    public ConversationEngine(SessionRepository sessionRepository,
                              MessageRepository messageRepository,
                              ActivityContentService activityContentService,
                              AiProviderRegistry aiProviderRegistry,
                              PromptAssembler promptAssembler,
                              CapabilityRegistry capabilityRegistry,
                              @Value("${booki.conversation.history-window:20}") int historyWindow) {
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
        this.activityContentService = activityContentService;
        this.aiProviderRegistry = aiProviderRegistry;
        this.promptAssembler = promptAssembler;
        this.capabilityRegistry = capabilityRegistry;
        this.historyWindow = Math.max(1, historyWindow);
    }

    public ConversationResult converse(ConversationRequest request) {
        Session session = sessionRepository.findByIdAndUserId(request.sessionId(), request.userId())
                .orElseThrow(() -> new NoSuchElementException("Session not found"));

        // Build the window from the history that exists BEFORE this turn, so the
        // new user message is sent once (as the prompt), not also echoed inside
        // the context. Fixes the old bug where limit(20) took the OLDEST 20.
        List<AiProvider.Message> history = recentHistory(session.getId());

        Message userMessage = persist(session, Message.Speaker.USER, request.inputType(), request.text());

        AiProvider provider = aiProviderRegistry.get(session.getAiProvider());
        ActivityContent content = resolveActivityContent(session, request, provider);

        String answer;
        try {
            answer = generateAnswer(request, session, history, provider, content);
        } catch (AiProviderException e) {
            // The user's turn stays in history; we simply don't fabricate a reply.
            throw new ConversationFailedException(UNAVAILABLE, e);
        }

        Message botMessage = persist(session, Message.Speaker.BOOKI, Message.InputType.TEXT, answer);
        return new ConversationResult(userMessage, botMessage);
    }

    /**
     * The shared activity range (same one Quiz/Summary use — see
     * {@code ActivityRangeContext} on the frontend) is required on every turn,
     * chat included: there is exactly one range concept in BooKI, no per-message
     * override. Resolves to either an uploaded-document reference or, for a
     * provider with no document support, plain text extracted from that range.
     */
    private ActivityContent resolveActivityContent(Session session, ConversationRequest request, AiProvider provider) {
        if (request.pageStart() == null || request.pageEnd() == null) {
            throw new IllegalArgumentException("pageStart and pageEnd are required");
        }
        int pageCount = session.getDocument().getPageCount();
        int start = Math.max(1, Math.min(request.pageStart(), pageCount));
        int end = Math.max(start, Math.min(request.pageEnd(), pageCount));
        return activityContentService.resolve(session.getDocument(), provider, start, end);
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
        AiProvider provider = aiProviderRegistry.get(session.getAiProvider());
        ActivityContent content = resolveActivityContent(session, request, provider);
        CapabilityInvocation invocation = new CapabilityInvocation(session, request.text(), history, content);

        Optional<ConversationCapability> hinted = hintedCapability(request, session);
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

        String systemPrompt = promptAssembler.forChat(session, activityContentService.documentTextFor(content),
                capabilityRegistry.routerInstructions(promptAssembler.enabledCapabilities(session)));

        // Native token streaming has no document-attached variant yet (only
        // ClaudeProvider streams at all, via StreamingAiProvider) — a
        // DocumentReference here still gets the range instruction in the prompt,
        // just not the file itself. Unreachable from any transport today (no
        // HTTP endpoint calls this — see ADR-010); revisit if that changes.
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
            Optional<ConversationCapability> routed = gating
                    ? routedCapability(fullText, promptAssembler.enabledCapabilities(session))
                    : Optional.empty();
            if (routed.isPresent()) {
                // A directive is short and starts with '{', so it was never flushed — nothing bogus was streamed.
                String answer;
                try {
                    answer = routed.get().execute(invocation);
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

    private Optional<ConversationCapability> hintedCapability(ConversationRequest request, Session session) {
        String hint = request.capabilityHint();
        if (hint == null || hint.isBlank()) {
            return Optional.empty();
        }
        ConversationCapability capability = capabilityRegistry.find(hint)
                .orElseThrow(() -> new IllegalArgumentException("Unknown capability: " + hint));
        if (!isEnabled(session, hint)) {
            throw new IllegalArgumentException(
                    "The \"" + hint + "\" capability is turned off for this session's AI Profile.");
        }
        return Optional.of(capability);
    }

    /** The capability a model reply routes to, iff it's a bare directive for an enabled, known capability. */
    private Optional<ConversationCapability> routedCapability(String reply, Set<Capability> enabled) {
        return capabilityRegistry.parseDirective(reply)
                .filter(name -> containsWire(enabled, name))
                .flatMap(capabilityRegistry::find);
    }

    private boolean isEnabled(Session session, String wire) {
        return containsWire(promptAssembler.enabledCapabilities(session), wire);
    }

    private static boolean containsWire(Set<Capability> enabled, String wire) {
        try {
            return enabled.contains(Capability.ofWire(wire));
        } catch (IllegalArgumentException e) {
            return false;
        }
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
    private String generateAnswer(ConversationRequest request, Session session, List<AiProvider.Message> history,
                                  AiProvider provider, ActivityContent content) {
        CapabilityInvocation invocation = new CapabilityInvocation(session, request.text(), history, content);

        Optional<ConversationCapability> hinted = hintedCapability(request, session);
        if (hinted.isPresent()) {
            return hinted.get().execute(invocation);
        }

        Set<Capability> enabled = promptAssembler.enabledCapabilities(session);
        String systemPrompt = promptAssembler.forChat(session, activityContentService.documentTextFor(content),
                capabilityRegistry.routerInstructions(enabled));
        long startedAt = System.currentTimeMillis();
        String reply;
        try {
            reply = activityContentService.converse(provider, content, systemPrompt, history, request.text());
        } catch (RuntimeException e) {
            log.warn("AI call failed provider={} model={} durationMs={}",
                    provider.key(), provider.model(), System.currentTimeMillis() - startedAt);
            throw e;
        }
        log.info("AI call completed provider={} model={} durationMs={}",
                provider.key(), provider.model(), System.currentTimeMillis() - startedAt);

        return routedCapability(reply, enabled)
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

    private Message persist(Session session, Message.Speaker speaker, Message.InputType inputType, String text) {
        Message message = new Message();
        message.setSession(session);
        message.setSpeaker(speaker);
        message.setInputType(inputType);
        message.setMessage(text);
        return messageRepository.save(message);
    }
}
