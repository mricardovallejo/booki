package com.booki.service.impl;

import com.booki.domain.DocumentPage;
import com.booki.domain.ProfileMaster;
import com.booki.domain.QuizAttempt;
import com.booki.domain.Session;
import com.booki.domain.User;
import com.booki.dto.GenerateQuizRequest;
import com.booki.dto.QuizAnswerResponse;
import com.booki.dto.QuizAttemptResponse;
import com.booki.dto.QuizConfigResponse;
import com.booki.dto.QuizGenerateResponse;
import com.booki.dto.QuizQuestionResponse;
import com.booki.dto.QuizReportResponse;
import com.booki.dto.SubmitQuizAnswerRequest;
import com.booki.repository.DocumentPageRepository;
import com.booki.repository.ProfileMasterRepository;
import com.booki.repository.QuizAttemptRepository;
import com.booki.repository.SessionRepository;
import com.booki.repository.UserRepository;
import com.booki.service.QuizService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.function.Function;

/**
 * Mock-equivalent keyword-overlap grading, ported faithfully from the
 * Node mock so its documented behavior in docs/openapi.yaml stays accurate.
 * A future pass can swap grading for a real AI judgment call without
 * changing the {@code {correct, score, feedback}} response shape.
 */
@Service
@RequiredArgsConstructor
public class QuizServiceImpl implements QuizService {

    private final SessionRepository sessionRepository;
    private final DocumentPageRepository documentPageRepository;
    private final ProfileMasterRepository profileMasterRepository;
    private final QuizAttemptRepository quizAttemptRepository;
    private final UserRepository userRepository;

    private static final Set<String> SUPPORTED_LANGUAGES = Set.of("en", "es", "fr");
    private static final Set<String> DIFFICULTIES = Set.of("easy", "medium", "hard");

    private static final Map<String, Function<Integer, String>> QUIZ_TEMPLATES = Map.of(
            "en", n -> "In your own words, what does page " + n + " explain?",
            "es", n -> "Con tus propias palabras, ¿qué explica la página " + n + "?",
            "fr", n -> "Avec tes propres mots, qu'est-ce que la page " + n + " explique ?"
    );

    private record DifficultySettings(double threshold, int denomCap) {
    }

    private static final Map<String, DifficultySettings> DIFFICULTY_SETTINGS = Map.of(
            "easy", new DifficultySettings(0.15, 4),
            "medium", new DifficultySettings(0.25, 6),
            "hard", new DifficultySettings(0.4, 8)
    );

    private record Feedback(String correct, String incorrect) {
    }

    private static final Map<String, Feedback> FEEDBACK = Map.of(
            "en", new Feedback(
                    "Nice! Your answer covers key ideas from this page.",
                    "Not quite — try rereading the page and mentioning its key terms."),
            "es", new Feedback(
                    "¡Bien! Tu respuesta cubre ideas clave de esta página.",
                    "No del todo — vuelve a leer la página y menciona sus términos clave."),
            "fr", new Feedback(
                    "Bien joué ! Ta réponse couvre les idées clés de cette page.",
                    "Pas tout à fait — relis la page et mentionne ses termes clés.")
    );

    private static final Map<String, Function<String, String>> USER_NOTE_PREFIX = Map.of(
            "en", note -> " (Keeping in mind what you told BooKI about yourself: \"" + note + "\")",
            "es", note -> " (Recordando lo que le contaste a BooKI sobre ti: \"" + note + "\")",
            "fr", note -> " (En tenant compte de ce que tu as dit à BooKI à ton sujet : \"" + note + "\")"
    );

    private static final Set<String> STOPWORDS = Set.of(
            "the", "and", "for", "with", "that", "this", "from", "are", "was", "were", "have", "has",
            "les", "des", "une", "pour", "dans", "sont", "avec",
            "los", "las", "del", "que", "una", "para", "como", "sus", "por"
    );

    @Override
    public QuizGenerateResponse generateQuiz(Long userId, Long sessionId, GenerateQuizRequest request) {
        Session session = findOwned(userId, sessionId);
        String lang = resolveLanguage(session.getLanguage());

        Long resolvedMasterId = request.getProfileMasterId() != null
                ? request.getProfileMasterId()
                : session.getProfileMaster() != null ? session.getProfileMaster().getId() : null;
        String resolvedDifficulty = resolveDifficulty(
                request.getDifficulty() != null ? request.getDifficulty() : session.getDifficulty());
        int questionCount = clamp(request.getQuestionCount() == null ? 3 : request.getQuestionCount(), 1, 10);

        ProfileMaster master = resolvedMasterId != null ? profileMasterRepository.findById(resolvedMasterId).orElse(null) : null;
        User user = userRepository.findById(userId).orElse(null);
        Function<Integer, String> template = QUIZ_TEMPLATES.getOrDefault(lang, QUIZ_TEMPLATES.get("en"));

        List<DocumentPage> pages = documentPageRepository.findByDocumentIdAndPageNumberBetweenOrderByPageNumberAsc(
                session.getDocument().getId(), session.getStartPage(), session.getEndPage());

        List<QuizQuestionResponse> questions = pages.stream()
                .limit(questionCount)
                .map(p -> {
                    String question = template.apply(p.getPageNumber());
                    if (p.getPageNumber().equals(pages.get(0).getPageNumber())
                            && user != null && user.getSystemPrompt() != null && !user.getSystemPrompt().isBlank()) {
                        question += USER_NOTE_PREFIX.getOrDefault(lang, USER_NOTE_PREFIX.get("en"))
                                .apply(user.getSystemPrompt());
                    }
                    return new QuizQuestionResponse(p.getPageNumber(), p.getPageNumber(), question);
                })
                .toList();

        QuizConfigResponse config = new QuizConfigResponse(
                resolvedMasterId, master != null ? master.getName() : null, resolvedDifficulty, questions.size());
        return new QuizGenerateResponse(questions, config);
    }

    @Override
    public QuizAnswerResponse submitAnswer(Long userId, Long sessionId, SubmitQuizAnswerRequest request) {
        Session session = findOwned(userId, sessionId);
        String lang = resolveLanguage(session.getLanguage());
        String difficulty = resolveDifficulty(
                request.getDifficulty() != null ? request.getDifficulty() : session.getDifficulty());

        DocumentPage page = documentPageRepository
                .findByDocumentIdAndPageNumberBetweenOrderByPageNumberAsc(
                        session.getDocument().getId(), request.getPageNumber(), request.getPageNumber())
                .stream().findFirst()
                .orElse(null);

        boolean isFirstAttempt = quizAttemptRepository.countBySessionId(sessionId) == 0;
        String answer = request.getAnswer() == null ? "" : request.getAnswer();

        boolean correct;
        double score;
        String feedback;

        if (page == null) {
            correct = false;
            score = 0;
            feedback = "Page not found in this session.";
        } else {
            DifficultySettings settings = DIFFICULTY_SETTINGS.getOrDefault(difficulty, DIFFICULTY_SETTINGS.get("medium"));
            Set<String> pageWords = tokenize(page.getExtractedText());
            Set<String> answerWords = tokenizeRaw(answer);
            long matches = answerWords.stream().filter(pageWords::contains).count();
            int denom = Math.max(1, Math.min(settings.denomCap(), pageWords.size()));
            score = Math.min(1.0, (double) matches / denom);
            correct = score >= settings.threshold() && !answer.isBlank();
            Feedback fb = FEEDBACK.getOrDefault(lang, FEEDBACK.get("en"));
            feedback = correct ? fb.correct() : fb.incorrect();
        }

        User user = userRepository.findById(userId).orElse(null);
        if (isFirstAttempt && user != null && user.getSystemPrompt() != null && !user.getSystemPrompt().isBlank()) {
            feedback += USER_NOTE_PREFIX.getOrDefault(lang, USER_NOTE_PREFIX.get("en")).apply(user.getSystemPrompt());
        }

        Long masterId = request.getProfileMasterId() != null
                ? request.getProfileMasterId()
                : session.getProfileMaster() != null ? session.getProfileMaster().getId() : null;

        QuizAttempt attempt = new QuizAttempt();
        attempt.setSession(session);
        attempt.setPageNumber(request.getPageNumber());
        attempt.setQuestion(request.getQuestion() == null ? "" : request.getQuestion());
        attempt.setAnswer(answer);
        attempt.setDifficulty(difficulty);
        attempt.setProfileMaster(masterId != null ? profileMasterRepository.findById(masterId).orElse(null) : null);
        attempt.setCorrect(correct);
        attempt.setScore(score);
        attempt.setFeedback(feedback);
        quizAttemptRepository.save(attempt);

        return new QuizAnswerResponse(correct, round2(score), feedback);
    }

    @Override
    public QuizReportResponse getReport(Long userId, Long sessionId) {
        Session session = findOwned(userId, sessionId);
        List<QuizAttempt> attempts = quizAttemptRepository.findBySessionIdOrderByCreatedAtAsc(session.getId());

        List<QuizAttemptResponse> responses = attempts.stream().map(a -> new QuizAttemptResponse(
                a.getId(), a.getPageNumber(), a.getQuestion(), a.getAnswer(), a.getCorrect(), round2(a.getScore()),
                a.getFeedback(), a.getDifficulty(), a.getProfileMaster() != null ? a.getProfileMaster().getName() : null,
                a.getCreatedAt()
        )).toList();

        int total = responses.size();
        long correctCount = responses.stream().filter(QuizAttemptResponse::isCorrect).count();
        double avg = total == 0 ? 0 : responses.stream().mapToDouble(QuizAttemptResponse::getScore).average().orElse(0);

        QuizReportResponse.Summary summary = new QuizReportResponse.Summary(
                total, (int) correctCount, total - (int) correctCount, (int) Math.round(avg * 100));
        return new QuizReportResponse(responses, summary);
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

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    /** Words > 4 chars, lowercased, letters-only, minus stopwords — matches the mock's tokenizer. */
    private Set<String> tokenize(String text) {
        Set<String> words = new LinkedHashSet<>();
        for (String w : text.toLowerCase().replaceAll("[^\\p{L}\\s]", "").split("\\s+")) {
            if (w.length() > 4 && !STOPWORDS.contains(w)) {
                words.add(w);
            }
        }
        return words;
    }

    private Set<String> tokenizeRaw(String text) {
        Set<String> words = new LinkedHashSet<>();
        for (String w : text.toLowerCase().replaceAll("[^\\p{L}\\s]", "").split("\\s+")) {
            if (!w.isBlank()) {
                words.add(w);
            }
        }
        return words;
    }
}
