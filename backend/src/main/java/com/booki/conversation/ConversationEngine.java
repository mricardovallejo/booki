package com.booki.conversation;

import com.booki.ai.AiProvider;
import com.booki.ai.AiProviderException;
import com.booki.ai.AiProviderRegistry;
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

    private final SessionRepository sessionRepository;
    private final MessageRepository messageRepository;
    private final DocumentPageRepository documentPageRepository;
    private final AiProviderRegistry aiProviderRegistry;
    private final SessionContextBuilder sessionContextBuilder;
    private final int historyWindow;
    private final int maxContextChars;

    public ConversationEngine(SessionRepository sessionRepository,
                              MessageRepository messageRepository,
                              DocumentPageRepository documentPageRepository,
                              AiProviderRegistry aiProviderRegistry,
                              SessionContextBuilder sessionContextBuilder,
                              @Value("${booki.conversation.history-window:20}") int historyWindow,
                              @Value("${booki.conversation.max-context-chars:24000}") int maxContextChars) {
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
        this.documentPageRepository = documentPageRepository;
        this.aiProviderRegistry = aiProviderRegistry;
        this.sessionContextBuilder = sessionContextBuilder;
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

        String systemPrompt = sessionContextBuilder.buildSystemPrompt(session, buildContextText(session));

        String answer;
        try {
            answer = aiProviderRegistry.get(session.getAiProvider())
                    .converse(systemPrompt, history, request.text());
        } catch (AiProviderException e) {
            // The user's turn stays in history; we simply don't fabricate a reply.
            throw new ConversationFailedException(
                    "The reading assistant is temporarily unavailable. Please try again in a moment.", e);
        }

        Message botMessage = persist(session, Message.Speaker.BOOKI, Message.InputType.TEXT, answer);
        return new ConversationResult(userMessage, botMessage);
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
