package com.booki.service.impl;

import com.booki.ai.AiProviderRegistry;
import com.booki.conversation.ConversationEngine;
import com.booki.conversation.ConversationRequest;
import com.booki.conversation.ConversationResult;
import com.booki.domain.Document;
import com.booki.domain.Message;
import com.booki.domain.ProfileMaster;
import com.booki.domain.Session;
import com.booki.dto.MessageRequest;
import com.booki.dto.MessageResponse;
import com.booki.dto.SessionContextResponse;
import com.booki.dto.SessionNotificationResponse;
import com.booki.dto.SessionProgressResponse;
import com.booki.dto.SessionRequest;
import com.booki.dto.SessionResponse;
import com.booki.repository.DocumentRepository;
import com.booki.repository.MessageRepository;
import com.booki.repository.ProfileMasterRepository;
import com.booki.repository.SessionRepository;
import com.booki.service.SessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

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
    private final ProfileMasterRepository profileMasterRepository;
    private final AiProviderRegistry aiProviderRegistry;
    private final SessionContextBuilder sessionContextBuilder;
    private final SessionProgressCalculator progressCalculator;
    private final ConversationEngine conversationEngine;

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
    public SessionResponse createSession(Long userId, SessionRequest request) {
        Document document = documentRepository.findByIdAndUserId(request.getDocumentId(), userId)
                .orElseThrow(() -> new NoSuchElementException("Document not found"));

        if (request.getStartPage() > request.getEndPage()) {
            throw new IllegalArgumentException("startPage must be less than or equal to endPage");
        }
        if (request.getEndPage() > document.getPageCount()) {
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
        session.setEndPage(request.getEndPage());
        session.setCurrentPage(request.getStartPage());
        session.setDifficulty(resolveDifficulty(request.getDifficulty()));
        session.setLanguage(sessionContextBuilder.resolveLanguage(request.getLanguage()));
        session.setAiProvider(request.getAiProvider());

        if (request.getProfileMasterId() != null) {
            ProfileMaster master = profileMasterRepository.findByIdAndUserId(request.getProfileMasterId(), userId).orElse(null);
            session.setProfileMaster(master);
        }
        session.setConfigJson("{}");

        sessionRepository.save(session);
        return toResponse(session);
    }

    @Override
    public SessionResponse getSession(Long userId, Long sessionId) {
        return toResponse(findOwned(userId, sessionId));
    }

    @Override
    public SessionContextResponse getContext(Long userId, Long sessionId) {
        Session session = findOwned(userId, sessionId);
        return sessionContextBuilder.buildContext(session);
    }

    @Override
    public SessionResponse updateCurrentPage(Long userId, Long sessionId, Integer currentPage) {
        Session session = findOwned(userId, sessionId);
        if (currentPage == null || currentPage < session.getStartPage() || currentPage > session.getEndPage()) {
            throw new IllegalArgumentException(
                    "currentPage must be between " + session.getStartPage() + " and " + session.getEndPage());
        }
        session.setCurrentPage(currentPage);
        sessionRepository.save(session);
        return toResponse(session);
    }

    @Override
    public List<MessageResponse> getMessages(Long userId, Long sessionId) {
        Session session = findOwned(userId, sessionId);
        return messageRepository.findBySessionIdOrderByCreatedAtAsc(session.getId()).stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public MessageResponse sendMessage(Long userId, Long sessionId, MessageRequest request) {
        // Conversational orchestration now lives in ConversationEngine, shared by
        // text, quick actions and (later) voice. This stays as the REST adapter.
        ConversationResult result = conversationEngine.converse(new ConversationRequest(
                userId, sessionId, request.getMessage(), parseInputType(request.getInputType())));
        return toResponse(result.botMessage());
    }

    @Override
    public SessionProgressResponse getProgress(Long userId, Long sessionId) {
        return progressCalculator.compute(findOwned(userId, sessionId));
    }

    @Override
    public List<SessionNotificationResponse> getNotifications(Long userId, Long sessionId) {
        Session session = findOwned(userId, sessionId);
        Map<String, String> t = NOTIF_TEXT.get(sessionContextBuilder.resolveLanguage(session.getLanguage()));
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
        if (progress.getQuizzesTaken() == 0) {
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
        response.setProfileMasterId(session.getProfileMaster() != null ? session.getProfileMaster().getId() : null);
        response.setLanguage(session.getLanguage());
        response.setAiProvider(aiProviderRegistry.resolveName(session.getAiProvider()));
        response.setCreatedAt(session.getCreatedAt());
        return response;
    }

    private MessageResponse toResponse(Message message) {
        MessageResponse response = new MessageResponse();
        response.setId(message.getId());
        response.setSpeaker(message.getSpeaker().name());
        response.setInputType(message.getInputType().name());
        response.setMessage(message.getMessage());
        response.setCreatedAt(message.getCreatedAt());
        return response;
    }
}
