package com.booki.service.impl;

import com.booki.ai.AiProvider;
import com.booki.domain.Document;
import com.booki.domain.DocumentPage;
import com.booki.domain.Message;
import com.booki.domain.ProfileMaster;
import com.booki.domain.Session;
import com.booki.domain.User;
import com.booki.dto.MessageRequest;
import com.booki.dto.MessageResponse;
import com.booki.dto.SessionContextResponse;
import com.booki.dto.SessionNotificationResponse;
import com.booki.dto.SessionProgressResponse;
import com.booki.dto.SessionRequest;
import com.booki.dto.SessionResponse;
import com.booki.repository.DocumentPageRepository;
import com.booki.repository.DocumentRepository;
import com.booki.repository.MessageRepository;
import com.booki.repository.ProfileMasterRepository;
import com.booki.repository.SessionRepository;
import com.booki.repository.UserRepository;
import com.booki.service.SessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SessionServiceImpl implements SessionService {

    private final SessionRepository sessionRepository;
    private final MessageRepository messageRepository;
    private final DocumentRepository documentRepository;
    private final DocumentPageRepository documentPageRepository;
    private final ProfileMasterRepository profileMasterRepository;
    private final UserRepository userRepository;
    private final AiProvider aiProvider;
    private final SessionProgressCalculator progressCalculator;

    private static final Set<String> SUPPORTED_LANGUAGES = Set.of("en", "es", "fr");
    private static final Set<String> DIFFICULTIES = Set.of("easy", "medium", "hard");
    private static final Map<String, String> LANGUAGE_NAMES = Map.of("en", "English", "es", "Spanish", "fr", "French");

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

    private static final String APP_PROMPT_TEMPLATE = """
            BooKI is a reading companion, not an authority figure. Respond in %s. \
            Keep the tone encouraging, never scold the reader for a wrong answer, \
            and always ground your answers in the session's page range below.""";

    @Override
    public SessionResponse createSession(Long userId, SessionRequest request) {
        Document document = documentRepository.findByIdAndUserId(request.getDocumentId(), userId)
                .orElseThrow(() -> new NoSuchElementException("Document not found"));

        Session session = new Session();
        session.setUser(document.getUser());
        session.setDocument(document);
        session.setStartPage(request.getStartPage());
        session.setEndPage(request.getEndPage());
        session.setCurrentPage(request.getStartPage());
        session.setDifficulty(resolveDifficulty(request.getDifficulty()));
        session.setLanguage(resolveLanguage(request.getLanguage()));

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
        return buildContext(session);
    }

    @Override
    public SessionResponse updateCurrentPage(Long userId, Long sessionId, Integer currentPage) {
        Session session = findOwned(userId, sessionId);
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
        Session session = findOwned(userId, sessionId);

        Message userMessage = new Message();
        userMessage.setSession(session);
        userMessage.setSpeaker(Message.Speaker.USER);
        userMessage.setInputType(Message.InputType.valueOf(request.getInputType()));
        userMessage.setMessage(request.getMessage());
        messageRepository.save(userMessage);

        List<DocumentPage> pages = documentPageRepository
                .findByDocumentIdAndPageNumberBetweenOrderByPageNumberAsc(
                        session.getDocument().getId(), session.getStartPage(), session.getEndPage());
        String contextText = pages.stream()
                .map(p -> "[Page " + p.getPageNumber() + "]\n" + p.getExtractedText())
                .collect(Collectors.joining("\n\n"));

        List<Message> recent = messageRepository.findBySessionIdOrderByCreatedAtAsc(session.getId())
                .stream()
                .limit(20)
                .toList();
        List<AiProvider.Message> aiContext = recent.stream()
                .map(m -> new AiProvider.Message(
                        m.getSpeaker() == Message.Speaker.USER ? "user" : "assistant",
                        m.getMessage()))
                .toList();

        String systemPrompt = buildSystemPrompt(session, contextText);
        String answer = aiProvider.converse(systemPrompt, aiContext, request.getMessage());

        Message botMessage = new Message();
        botMessage.setSession(session);
        botMessage.setSpeaker(Message.Speaker.BOOKI);
        botMessage.setInputType(Message.InputType.TEXT);
        botMessage.setMessage(answer);
        messageRepository.save(botMessage);

        return toResponse(botMessage);
    }

    @Override
    public SessionProgressResponse getProgress(Long userId, Long sessionId) {
        return progressCalculator.compute(findOwned(userId, sessionId));
    }

    @Override
    public List<SessionNotificationResponse> getNotifications(Long userId, Long sessionId) {
        Session session = findOwned(userId, sessionId);
        Map<String, String> t = NOTIF_TEXT.get(resolveLanguage(session.getLanguage()));
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

    private String resolveLanguage(String language) {
        return (language != null && SUPPORTED_LANGUAGES.contains(language)) ? language : "en";
    }

    private String resolveDifficulty(String difficulty) {
        return (difficulty != null && DIFFICULTIES.contains(difficulty)) ? difficulty : "medium";
    }

    /**
     * The three layers that shape every AI call for this session: the app's
     * own baseline behavior (language-aware), the Profile Master's persona,
     * and the user's own stated preferences. Also served directly via
     * GET /sessions/{id}/context for transparency.
     */
    private SessionContextResponse buildContext(Session session) {
        String lang = resolveLanguage(session.getLanguage());
        String appPrompt = APP_PROMPT_TEMPLATE.formatted(LANGUAGE_NAMES.getOrDefault(lang, "English"));

        String masterPrompt = session.getProfileMaster() != null
                ? session.getProfileMaster().getSystemPrompt()
                : null;

        User user = userRepository.findById(session.getUser().getId()).orElse(null);
        String userPrompt = (user != null && user.getSystemPrompt() != null && !user.getSystemPrompt().isBlank())
                ? user.getSystemPrompt()
                : null;

        return new SessionContextResponse(appPrompt, masterPrompt, userPrompt);
    }

    private String buildSystemPrompt(Session session, String contextText) {
        SessionContextResponse context = buildContext(session);

        StringBuilder sb = new StringBuilder();
        sb.append(context.getAppPrompt());
        if (context.getMasterPrompt() != null) {
            sb.append("\n\nYour persona for this session: ").append(context.getMasterPrompt());
        }
        if (context.getUserPrompt() != null) {
            sb.append("\n\nWhat this reader has told you about themselves: ").append(context.getUserPrompt());
        }
        sb.append("\n\nSession difficulty: ").append(session.getDifficulty());
        sb.append("\nDocument title: ").append(session.getDocument().getTitle());
        sb.append("\nSession page range: ").append(session.getStartPage())
                .append("-").append(session.getEndPage());
        sb.append("\nReader's current page: ").append(session.getCurrentPage());
        sb.append("\n\nDOCUMENT CONTEXT:\n").append(contextText);
        return sb.toString();
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
