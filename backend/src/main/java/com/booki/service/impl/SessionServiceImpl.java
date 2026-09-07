package com.booki.service.impl;

import com.booki.ai.AiProviderRegistry;
import com.booki.conversation.ConversationEngine;
import com.booki.conversation.ConversationRequest;
import com.booki.conversation.ConversationResult;
import com.booki.domain.AiProfile;
import com.booki.domain.Capability;
import com.booki.domain.Document;
import com.booki.domain.Message;
import com.booki.domain.Session;
import com.booki.dto.MessageRequest;
import com.booki.dto.MessageResponse;
import com.booki.dto.SessionContextResponse;
import com.booki.dto.SessionNotificationResponse;
import com.booki.dto.SessionProgressResponse;
import com.booki.dto.SessionRequest;
import com.booki.dto.SessionResponse;
import com.booki.prompt.PromptAssembler;
import com.booki.repository.AiProfileRepository;
import com.booki.repository.DocumentRepository;
import com.booki.repository.MessageRepository;
import com.booki.repository.SessionRepository;
import com.booki.service.ReaderProfileService;
import com.booki.service.SessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class SessionServiceImpl implements SessionService {

    private final SessionRepository sessionRepository;
    private final MessageRepository messageRepository;
    private final DocumentRepository documentRepository;
    private final AiProfileRepository aiProfileRepository;
    private final AiProviderRegistry aiProviderRegistry;
    private final PromptAssembler promptAssembler;
    private final SessionProgressCalculator progressCalculator;
    private final ConversationEngine conversationEngine;
    private final ReaderProfileService readerProfileService;

    private static final Set<String> DIFFICULTIES = Set.of("easy", "medium", "hard");

    private static final Map<String, Map<String, String>> NOTIF_TEXT = Map.of(
            "en", Map.of(
                    "halfway", "You are halfway through this session — keep going!",
                    "done", "You finished all the pages in this session. Nice work!",
                    "sayHi", "Say hi to BooKI to start the conversation.",
                    "tryQuiz", "Try a quick quiz to test your understanding."
            ),
            "es", Map.of(
                    "halfway", "Vas a la mitad de esta sesión, ¡sigue así!",
                    "done", "¡Terminaste todas las páginas de esta sesión!",
                    "sayHi", "Salúdale a BooKI para empezar la conversación.",
                    "tryQuiz", "Prueba un quiz rápido para reforzar lo leído."
            ),
            "fr", Map.of(
                    "halfway", "Tu es à mi-chemin de cette session, continue !",
                    "done", "Tu as terminé toutes les pages de cette session !",
                    "sayHi", "Dis bonjour à BooKI pour démarrer la conversation.",
                    "tryQuiz", "Essaie un petit quiz pour tester ta compréhension."
            )
    );

    @Override
    @Transactional
    public SessionResponse createSession(Long userId, SessionRequest request) {
        Document document = documentRepository.findByIdAndUserId(request.getDocumentId(), userId)
                .orElseThrow(() -> new NoSuchElementException("Document not found"));

        int initialEndPage = request.getEndPage() != null ? request.getEndPage() : request.getStartPage();
        if (request.getStartPage() > document.getPageCount()) {
            throw new IllegalArgumentException(
                    "startPage exceeds the document's page count (" + document.getPageCount() + ")");
        }
        if (request.getStartPage() > initialEndPage) {
            throw new IllegalArgumentException("startPage must be less than or equal to endPage");
        }
        if (initialEndPage > document.getPageCount()) {
            throw new IllegalArgumentException(
                    "endPage exceeds the document's page count (" + document.getPageCount() + ")");
        }
        if (request.getAiProvider() != null && !aiProviderRegistry.availableProviders().contains(request.getAiProvider())) {
            throw new IllegalArgumentException(
                    "aiProvider must be one of " + aiProviderRegistry.availableProviders());
        }

        Session session = new Session();
        session.setUser(document.getUser());
        session.setDocument(document);
        session.setStartPage(request.getStartPage());
        session.setEndPage(initialEndPage);
        session.setCurrentPage(request.getStartPage());
        session.setDifficulty(resolveDifficulty(request.getDifficulty()));
        session.setLanguage(promptAssembler.resolveLanguage(request.getLanguage()));
        session.setAiProvider(request.getAiProvider());
        session.setAiProfile(resolveAiProfile(userId, request.getAiProfileId()));
        session.setReaderProfile(readerProfileService.forNewSession(userId, request.getReaderProfileId()));

        sessionRepository.save(session);
        return toResponse(session);
    }

    /** The requested profile if it's the user's, otherwise their default, otherwise their first. */
    private AiProfile resolveAiProfile(Long userId, Long requestedId) {
        if (requestedId != null) {
            AiProfile requested = aiProfileRepository.findByIdAndUserId(requestedId, userId).orElse(null);
            if (requested != null) {
                return requested;
            }
        }
        return aiProfileRepository.findFirstByUserIdAndDefaultProfileTrueOrderByIdAsc(userId)
                .orElseGet(() -> aiProfileRepository.findByUserIdOrderByIdAsc(userId).stream().findFirst().orElse(null));
    }

    @Override
    @Transactional(readOnly = true)
    public SessionResponse getSession(Long userId, Long sessionId) {
        return toResponse(findOwned(userId, sessionId));
    }

    @Override
    @Transactional(readOnly = true)
    public SessionContextResponse getContext(Long userId, Long sessionId) {
        Session session = findOwned(userId, sessionId);
        return promptAssembler.describe(session);
    }

    @Override
    @Transactional
    public SessionResponse updateCurrentPage(Long userId, Long sessionId, Integer currentPage) {
        Session session = findOwned(userId, sessionId);
        int documentPageCount = session.getDocument().getPageCount();
        if (currentPage == null || currentPage < 1 || currentPage > documentPageCount) {
            throw new IllegalArgumentException(
                    "currentPage must be between 1 and " + documentPageCount);
        }
        session.setCurrentPage(currentPage);
        if (currentPage > session.getEndPage()) {
            session.setEndPage(currentPage);
        }
        sessionRepository.save(session);
        return toResponse(session);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MessageResponse> getMessages(Long userId, Long sessionId) {
        Session session = findOwned(userId, sessionId);
        return messageRepository.findBySessionIdOrderByCreatedAtAsc(session.getId()).stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public MessageResponse sendMessage(Long userId, Long sessionId, MessageRequest request) {
        // Deliberately NOT @Transactional: ConversationEngine spans a slow model
        // call and persists the user turn and the reply as separate units so a
        // provider failure never leaves a fake reply behind. A transaction here
        // would hold a DB connection open across that call and undo that design.
        ConversationResult result = conversationEngine.converse(new ConversationRequest(
                userId, sessionId, request.getMessage(), parseInputType(request.getInputType()),
                request.getCapabilityHint(), request.getPageStart(), request.getPageEnd()));
        return toResponse(result.botMessage());
    }

    @Override
    @Transactional(readOnly = true)
    public SessionProgressResponse getProgress(Long userId, Long sessionId) {
        return progressCalculator.compute(findOwned(userId, sessionId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<SessionNotificationResponse> getNotifications(Long userId, Long sessionId) {
        Session session = findOwned(userId, sessionId);
        Map<String, String> t = NOTIF_TEXT.get(promptAssembler.resolveLanguage(session.getLanguage()));
        SessionProgressResponse progress = progressCalculator.compute(session);

        List<SessionNotificationResponse> notifications = new java.util.ArrayList<>();
        if (progress.getPctRead() >= 100) {
            notifications.add(new SessionNotificationResponse(1, "progress", t.get("done"), java.time.Instant.now()));
        } else if (progress.getPctRead() >= 50) {
            notifications.add(new SessionNotificationResponse(1, "progress", t.get("halfway"), java.time.Instant.now()));
        }
        if (progress.getMessageCount() == 0) {
            notifications.add(new SessionNotificationResponse(2, "chat", t.get("sayHi"), java.time.Instant.now()));
        }
        if (progress.getQuestionsAnswered() == 0) {
            notifications.add(new SessionNotificationResponse(3, "quiz", t.get("tryQuiz"), java.time.Instant.now()));
        }
        return notifications;
    }

    private Session findOwned(Long userId, Long sessionId) {
        return sessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new NoSuchElementException("Session not found"));
    }

    private String resolveDifficulty(String difficulty) {
        return (difficulty != null && DIFFICULTIES.contains(difficulty)) ? difficulty : "medium";
    }

    private Message.InputType parseInputType(String inputType) {
        try {
            return Message.InputType.valueOf(inputType);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new IllegalArgumentException("inputType must be TEXT or VOICE");
        }
    }

    private SessionResponse toResponse(Session session) {
        SessionResponse response = new SessionResponse();
        response.setId(session.getId());
        response.setDocumentId(session.getDocument().getId());
        response.setTitle(session.getDocument().getTitle() + " (pages " + session.getStartPage() + "-" + session.getEndPage() + ")");
        response.setStartPage(session.getStartPage());
        response.setEndPage(session.getEndPage());
        response.setCurrentPage(session.getCurrentPage());
        response.setDifficulty(session.getDifficulty());
        response.setAiProfileId(session.getAiProfile() != null ? session.getAiProfile().getId() : null);
        response.setReaderProfileId(session.getReaderProfile() != null ? session.getReaderProfile().getId() : null);
        response.setEnabledCapabilities(promptAssembler.enabledCapabilities(session).stream()
                .sorted().map(Capability::wire).toList());
        response.setLanguage(session.getLanguage());
        response.setAiProvider(aiProviderRegistry.resolveName(session.getAiProvider()));
        response.setCreatedAt(session.getCreatedAt());
        return response;
    }

    private MessageResponse toResponse(Message message) {
        return MessageResponse.of(message);
    }
}
