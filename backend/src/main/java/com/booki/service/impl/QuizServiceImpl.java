package com.booki.service.impl;

import com.booki.ai.AiProvider;
import com.booki.ai.AiProviderRegistry;
import com.booki.domain.AiProfile;
import com.booki.domain.DocumentPage;
import com.booki.domain.QuizAttempt;
import com.booki.domain.Session;
import com.booki.domain.SlotKey;
import com.booki.prompt.PromptAssembler;
import com.booki.dto.GenerateQuizRequest;
import com.booki.dto.QuizAnswerResponse;
import com.booki.dto.QuizAttemptResponse;
import com.booki.dto.QuizConfigResponse;
import com.booki.dto.QuizGenerateResponse;
import com.booki.dto.QuizQuestionResponse;
import com.booki.dto.QuizReportResponse;
import com.booki.dto.SubmitQuizAnswerRequest;
import com.booki.repository.AiProfileRepository;
import com.booki.repository.DocumentPageRepository;
import com.booki.repository.QuizAttemptRepository;
import com.booki.repository.SessionRepository;
import com.booki.service.QuizService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Question generation and grading both call the session's chosen AI
 * provider ({@link AiProviderRegistry}), grounded in that page's reading
 * text plus the layered prompt {@link PromptAssembler} builds, with the
 * quiz-question / answer-grading SlotPrompts layered in. Reports (PDF
 * progress/quiz correction) stay template-based; only this in-session flow
 * is AI-driven.
 */
@Service
@RequiredArgsConstructor
public class QuizServiceImpl implements QuizService {

    private final SessionRepository sessionRepository;
    private final DocumentPageRepository documentPageRepository;
    private final AiProfileRepository aiProfileRepository;
    private final QuizAttemptRepository quizAttemptRepository;
    private final AiProviderRegistry aiProviderRegistry;
    private final PromptAssembler promptAssembler;

    private static final Set<String> DIFFICULTIES = Set.of("easy", "medium", "hard");

    private static final Pattern SCORE_PATTERN = Pattern.compile("SCORE:\\s*([0-9]*\\.?[0-9]+)");
    private static final Pattern FEEDBACK_PATTERN = Pattern.compile("FEEDBACK:\\s*(.+)", Pattern.DOTALL);

    /**
     * A single scale drives the whole quiz report: {@code correct} is just
     * "scored at least this", so the correction summary's correct-count and its
     * average score never tell contradictory stories.
     */
    private static final double PASS_SCORE = 0.6;

    @Override
    public QuizGenerateResponse generateQuiz(Long userId, Long sessionId, GenerateQuizRequest request) {
        Session session = findOwned(userId, sessionId);

        Long resolvedProfileId = request.getAiProfileId() != null
                ? request.getAiProfileId()
                : session.getAiProfile() != null ? session.getAiProfile().getId() : null;
        String resolvedDifficulty = resolveDifficulty(
                request.getDifficulty() != null ? request.getDifficulty() : session.getDifficulty());
        int questionCount = clamp(request.getQuestionCount() == null ? 3 : request.getQuestionCount(), 1, 20);
        int pageCount = session.getDocument().getPageCount();
        // Clamp rather than reject: a stale or oversized range from the client
        // self-corrects instead of erroring.
        int endPage = clamp(request.getEndPage() != null ? request.getEndPage() : pageCount, 1, pageCount);
        int startPage = clamp(request.getStartPage() != null ? request.getStartPage() : 1, 1, endPage);

        AiProfile profile = resolvedProfileId != null
                ? aiProfileRepository.findByIdAndUserId(resolvedProfileId, userId).orElse(null) : null;
        AiProvider provider = aiProviderRegistry.get(session.getAiProvider());

        List<DocumentPage> pages = documentPageRepository.findByDocumentIdAndPageNumberBetweenOrderByPageNumberAsc(
                session.getDocument().getId(), startPage, endPage);
        if (pages.isEmpty()) {
            throw new IllegalStateException("The selected pages have no extracted text to build questions from.");
        }

        // The number of questions is independent of how many pages the range
        // spans. Spread the questions evenly ACROSS the range (not clustered at
        // its start) so the quiz reflects the whole selection; a 1-page range
        // still yields as many questions as asked.
        int n = pages.size();
        List<QuizQuestionResponse> questions = new java.util.ArrayList<>(questionCount);
        for (int i = 0; i < questionCount; i++) {
            int idx = questionCount == 1 ? 0
                    : (int) Math.round((double) i * (n - 1) / (questionCount - 1));
            DocumentPage page = pages.get(Math.min(idx, n - 1));
            questions.add(new QuizQuestionResponse(i + 1, page.getPageNumber(),
                    questionForPage(session, page, resolvedDifficulty, provider, startPage, endPage)));
        }

        QuizConfigResponse config = new QuizConfigResponse(
                resolvedProfileId, profile != null ? profile.getName() : null, resolvedDifficulty, questions.size(),
                startPage, endPage);
        return new QuizGenerateResponse(questions, config);
    }

    @Override
    public String generateComprehensionQuestion(Session session, String pageContextText) {
        String difficulty = resolveDifficulty(session.getDifficulty());
        AiProvider provider = aiProviderRegistry.get(session.getAiProvider());

        String context = pageContextText;
        if (context == null || context.isBlank()) {
            int target = session.getCurrentPage() != null ? session.getCurrentPage() : session.getStartPage();
            DocumentPage page = firstPageInRange(session, target, target)
                    .orElseThrow(() -> new IllegalStateException(
                            "This session has no extracted pages to build a question from."));
            context = "[Page " + page.getPageNumber() + "]\n" + page.getExtractedText();
        }
        String systemPrompt = promptAssembler.forFunction(
                session, SlotKey.FN_QUIZ_QUESTION, difficulty, context);
        return provider.converse(systemPrompt, List.of(),
                "Write one quiz question using only the supplied page blocks. "
                        + "Do not ask about any page or information outside them.").strip();
    }

    private java.util.Optional<DocumentPage> firstPageInRange(Session session, int start, int end) {
        return documentPageRepository.findByDocumentIdAndPageNumberBetweenOrderByPageNumberAsc(
                session.getDocument().getId(), start, end).stream().findFirst();
    }

    private String questionForPage(Session session, DocumentPage page, String difficulty, AiProvider provider,
                                   int selectedStartPage, int selectedEndPage) {
        String systemPrompt = promptAssembler.forFunction(session, SlotKey.FN_QUIZ_QUESTION, difficulty,
                "[Page " + page.getPageNumber() + "]\n" + page.getExtractedText());
        return provider.converse(systemPrompt, List.of(),
                "The reader selected pages " + selectedStartPage + "-" + selectedEndPage
                        + ". Write one question using only the supplied Page " + page.getPageNumber()
                        + " block. Do not ask about any other page or outside information.").strip();
    }

    @Override
    public QuizAnswerResponse submitAnswer(Long userId, Long sessionId, SubmitQuizAnswerRequest request) {
        Session session = findOwned(userId, sessionId);
        String difficulty = resolveDifficulty(
                request.getDifficulty() != null ? request.getDifficulty() : session.getDifficulty());
        String answer = request.getAnswer() == null ? "" : request.getAnswer();

        DocumentPage page = documentPageRepository
                .findByDocumentIdAndPageNumberBetweenOrderByPageNumberAsc(
                        session.getDocument().getId(), request.getPageNumber(), request.getPageNumber())
                .stream().findFirst()
                .orElse(null);

        boolean correct;
        double score;
        String feedback;

        if (page == null) {
            correct = false;
            score = 0;
            feedback = "Page not found in this session.";
        } else {
            AiProvider provider = aiProviderRegistry.get(session.getAiProvider());
            String systemPrompt = promptAssembler.forFunction(session, SlotKey.FN_ANSWER_GRADING, difficulty,
                    "[Page " + page.getPageNumber() + "]\n" + page.getExtractedText());
            String instruction = "Question: " + (request.getQuestion() == null ? "" : request.getQuestion()) + "\n"
                    + "Reader's answer: " + (answer.isBlank() ? "(no answer given)" : answer)
                    + "\n\nGrade the answer now, in the required format.";

            String response = provider.converse(systemPrompt, List.of(), instruction);
            GradeResult grade = parseGrade(response);
            correct = grade.correct();
            score = grade.score();
            feedback = grade.feedback();
        }

        Long profileId = request.getAiProfileId() != null
                ? request.getAiProfileId()
                : session.getAiProfile() != null ? session.getAiProfile().getId() : null;

        QuizAttempt attempt = new QuizAttempt();
        attempt.setSession(session);
        attempt.setPageNumber(request.getPageNumber());
        attempt.setQuestion(request.getQuestion() == null ? "" : request.getQuestion());
        attempt.setAnswer(answer);
        attempt.setDifficulty(difficulty);
        attempt.setAiProfile(profileId != null
                ? aiProfileRepository.findByIdAndUserId(profileId, userId).orElse(null) : null);
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
                a.getFeedback(), a.getDifficulty(), a.getAiProfile() != null ? a.getAiProfile().getName() : null,
                a.getCreatedAt()
        )).toList();

        int total = responses.size();
        long correctCount = responses.stream().filter(QuizAttemptResponse::isCorrect).count();
        double avg = total == 0 ? 0 : responses.stream().mapToDouble(QuizAttemptResponse::getScore).average().orElse(0);

        QuizReportResponse.Summary summary = new QuizReportResponse.Summary(
                total, (int) correctCount, total - (int) correctCount, (int) Math.round(avg * 100));
        return new QuizReportResponse(responses, summary);
    }

    private record GradeResult(boolean correct, double score, String feedback) {
    }

    /** Parses the AI's SCORE/FEEDBACK reply; degrades gracefully (score 0, raw text as feedback) if the model didn't follow the format. {@code correct} is derived from the score (see {@link #PASS_SCORE}). Provider failures now surface as errors upstream rather than reaching here. */
    private GradeResult parseGrade(String response) {
        Matcher scoreMatcher = SCORE_PATTERN.matcher(response);
        Matcher feedbackMatcher = FEEDBACK_PATTERN.matcher(response);

        double score;
        try {
            score = scoreMatcher.find() ? Math.max(0, Math.min(1, Double.parseDouble(scoreMatcher.group(1)))) : 0.0;
        } catch (NumberFormatException e) {
            score = 0.0;
        }
        String feedback = feedbackMatcher.find() ? feedbackMatcher.group(1).strip() : response.strip();
        return new GradeResult(score >= PASS_SCORE, score, feedback);
    }

    private Session findOwned(Long userId, Long sessionId) {
        return sessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new NoSuchElementException("Session not found"));
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
}
