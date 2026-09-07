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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    private static final int MAX_CONTEXT_PAGES = 8;
    private static final int MAX_EXPLICIT_CONTEXT_PAGES = 20;
    private static final Pattern PAGE_RANGE = Pattern.compile(
            "(?iu)\\b(?:pages?|páginas?|paginas?)\\s*(\\d+)\\s*"
                    + "(?:-|–|—|to|through|a|à|hasta)\\s*"
                    + "(?:(?:the|la|las|le|les)\\s+)?"
                    + "(?:pages?|páginas?|paginas?)?\\s*(\\d+)");

    private final SessionRepository sessionRepository;
    private final MessageRepository messageRepository;
    private final DocumentPageRepository documentPageRepository;
    private final AiProviderRegistry aiProviderRegistry;
    private final PromptAssembler promptAssembler;
    private final CapabilityRegistry capabilityRegistry;
    private final int historyWindow;
    private final int maxContextChars;

    public ConversationEngine(SessionRepository sessionRepository,
                              MessageRepository messageRepository,
                              DocumentPageRepository documentPageRepository,
                              AiProviderRegistry aiProviderRegistry,
                              PromptAssembler promptAssembler,
                              CapabilityRegistry capabilityRegistry,
                              @Value("${booki.conversation.history-window:20}") int historyWindow,
                              @Value("${booki.conversation.max-context-chars:24000}") int maxContextChars) {
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
        this.documentPageRepository = documentPageRepository;
        this.aiProviderRegistry = aiProviderRegistry;
        this.promptAssembler = promptAssembler;
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

        String pageContext = buildContextText(session, request);

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
        String pageContext = buildContextText(session, request);
        CapabilityInvocation invocation = new CapabilityInvocation(session, request.text(), history, pageContext);

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

        String systemPrompt = promptAssembler.forChat(session, pageContext,
                capabilityRegistry.routerInstructions(promptAssembler.enabledCapabilities(session)));

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
    private String generateAnswer(ConversationRequest request, Session session,
                                  List<AiProvider.Message> history, String pageContext) {
        CapabilityInvocation invocation =
                new CapabilityInvocation(session, request.text(), history, pageContext);

        Optional<ConversationCapability> hinted = hintedCapability(request, session);
        if (hinted.isPresent()) {
            return hinted.get().execute(invocation);
        }

        Set<Capability> enabled = promptAssembler.enabledCapabilities(session);
        String systemPrompt = promptAssembler.forChat(session, pageContext,
                capabilityRegistry.routerInstructions(enabled));
        String providerName = aiProviderRegistry.resolveName(session.getAiProvider());
        AiProvider provider = aiProviderRegistry.get(session.getAiProvider());
        long startedAt = System.currentTimeMillis();
        String reply;
        try {
            reply = provider.converse(systemPrompt, history, request.text());
        } catch (RuntimeException e) {
            log.warn("AI call failed provider={} model={} durationMs={}",
                    providerName, provider.model(), System.currentTimeMillis() - startedAt);
            throw e;
        }
        log.info("AI call completed provider={} model={} durationMs={}",
                providerName, provider.model(), System.currentTimeMillis() - startedAt);

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

    /**
     * An explicitly requested range (up to 20 pages), or the current page and
     * up to seven recently read pages. Selection prioritizes the newest page
     * and restores reading order before sending. A session may span the whole
     * document, but a normal chat turn must not send the whole PDF.
     */
    private String buildContextText(Session session, ConversationRequest request) {
        String userText = request.text();
        int documentPageCount = session.getDocument().getPageCount();
        int currentPage = session.getCurrentPage() != null
                ? session.getCurrentPage() : session.getStartPage();
        int contextStart;
        int contextEnd;
        Optional<int[]> explicitRange = explicitPageRange(userText, documentPageCount);
        Optional<int[]> activityRange = activityPageRange(request, documentPageCount);
        if (explicitRange.isPresent()) {
            // A range typed into the message ("pages 4-6") always wins.
            contextStart = explicitRange.get()[0];
            contextEnd = explicitRange.get()[1];
        } else if (activityRange.isPresent()) {
            // A quick-action button carries the shared activity range; take the
            // last MAX_EXPLICIT_CONTEXT_PAGES of it so a wide range stays bounded.
            contextEnd = activityRange.get()[1];
            contextStart = Math.max(activityRange.get()[0], contextEnd - MAX_EXPLICIT_CONTEXT_PAGES + 1);
        } else {
            contextStart = currentPage >= session.getStartPage()
                    ? Math.max(session.getStartPage(), currentPage - MAX_CONTEXT_PAGES + 1)
                    : currentPage;
            contextEnd = currentPage;
        }
        List<DocumentPage> pages = documentPageRepository
                .findByDocumentIdAndPageNumberBetweenOrderByPageNumberAsc(
                        session.getDocument().getId(), contextStart, contextEnd);

        List<String> selected = new ArrayList<>();
        int usedChars = 0;
        for (int i = pages.size() - 1; i >= 0; i--) {
            DocumentPage page = pages.get(i);
            String block = "[Page " + page.getPageNumber() + "]\n" + page.getExtractedText();
            int separatorChars = selected.isEmpty() ? 0 : 2;
            if (usedChars + separatorChars + block.length() > maxContextChars) {
                if (selected.isEmpty()) {
                    String marker = "\n[Current page truncated to fit the AI context limit.]";
                    int textLimit = Math.max(0, maxContextChars - marker.length());
                    selected.add(block.substring(0, Math.min(textLimit, block.length())) + marker);
                }
                break;
            }
            selected.add(block);
            usedChars += separatorChars + block.length();
        }
        Collections.reverse(selected);
        return String.join("\n\n", selected);
    }

    /** The activity page range a quick action sent alongside the turn, clamped to the document. */
    private Optional<int[]> activityPageRange(ConversationRequest request, int documentPageCount) {
        Integer start = request.pageStart();
        Integer end = request.pageEnd();
        if (start == null || end == null) {
            return Optional.empty();
        }
        int s = Math.max(1, Math.min(start, documentPageCount));
        int e = Math.max(s, Math.min(end, documentPageCount));
        return Optional.of(new int[]{s, e});
    }

    private Optional<int[]> explicitPageRange(String userText, int documentPageCount) {
        if (userText == null || userText.isBlank()) {
            return Optional.empty();
        }
        Matcher matcher = PAGE_RANGE.matcher(userText);
        if (!matcher.find()) {
            return Optional.empty();
        }
        try {
            int startPage = Integer.parseInt(matcher.group(1));
            int endPage = Integer.parseInt(matcher.group(2));
            if (startPage < 1 || endPage < startPage || endPage > documentPageCount
                    || endPage - startPage + 1 > MAX_EXPLICIT_CONTEXT_PAGES) {
                return Optional.empty();
            }
            return Optional.of(new int[]{startPage, endPage});
        } catch (NumberFormatException ignored) {
            return Optional.empty();
        }
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
