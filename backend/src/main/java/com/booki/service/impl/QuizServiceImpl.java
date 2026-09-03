package com.booki.service.impl;

import com.booki.ai.AiProvider;
import com.booki.ai.AiProviderRegistry;
import com.booki.domain.AiProfile;
import com.booki.domain.DocumentPage;
import com.booki.domain.QuizAttempt;
import com.booki.domain.Session;
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
 * text plus the same prompt {@link SessionContextBuilder} builds for chat.
 * Reports (PDF progress/quiz correction) stay template-based; only this
 * in-session flow is AI-driven.
 */
@Service
@RequiredArgsConstructor
public class QuizServiceImpl implements QuizService {

    private final SessionRepository sessionRepository;
    private final DocumentPageRepository documentPageRepository;
    private final AiProfileRepository aiProfileRepository;
    private final QuizAttemptRepository quizAttemptRepository;
    private final AiProviderRegistry aiProviderRegistry;
    private final SessionContextBuilder sessionContextBuilder;

    private static final Set<String> DIFFICULTIES = Set.of("easy", "medium", "hard");

    private static final Pattern CORRECT_PATTERN = Pattern.compile("CORRECT:\\s*(yes|no)", Pattern.CASE_INSENSITIVE);
    private static final Pattern SCORE_PATTERN = Pattern.compile("SCORE:\\s*([0-9]*\\.?[0-9]+)");
    private static final Pattern FEEDBACK_PATTERN = Pattern.compile("FEEDBACK:\\s*(.+)");

    @Override
    public QuizGenerateResponse generateQuiz(Long userId, Long sessionId, GenerateQuizRequest request) {
        Session session = findOwned(userId, sessionId);
        String languageName = sessionContextBuilder.languageName(session.getLanguage());

        Long resolvedProfileId = request.getAiProfileId() != null
                ? request.getAiProfileId()
                : session.getAiProfile() != null ? session.getAiProfile().getId() : null;
        String resolvedDifficulty = resolveDifficulty(
                request.getDifficulty() != null ? request.getDifficulty() : session.getDifficulty());
        int questionCount = clamp(request.getQuestionCount() == null ? 3 : request.getQuestionCount(), 1, 10);

        AiProfile profile = resolvedProfileId != null
                ? aiProfileRepository.findByIdAndUserId(resolvedProfileId, userId).orElse(null) : null;
        AiProvider provider = aiProviderRegistry.get(session.getAiProvider());

        List<DocumentPage> pages = documentPageRepository.findByDocumentIdAndPageNumberBetweenOrderByPageNumberAsc(
                session.getDocument().getId(), session.getStartPage(), session.getEndPage());

        List<QuizQuestionResponse> questions = pages.stream()
                .limit(questionCount)
                .map(p -> new QuizQuestionResponse(p.getPageNumber(), p.getPageNumber(),
                        questionForPage(session, p, resolvedDifficulty, languageName, provider)))
                .toList();

        QuizConfigResponse config = new QuizConfigResponse(
                resolvedProfileId, profile != null ? profile.getName() : null, resolvedDifficulty, questions.size());
        return new QuizGenerateResponse(questions, config);
    }

    @Override
    public String generateComprehensionQuestion(Session session) {
        String languageName = sessionContextBuilder.languageName(session.getLanguage());
        String difficulty = resolveDifficulty(session.getDifficulty());
        AiProvider provider = aiProviderRegistry.get(session.getAiProvider());

        int target = session.getCurrentPage() != null ? session.getCurrentPage() : session.getStartPage();
        DocumentPage page = firstPageInRange(session, target, target)
                .or(() -> firstPageInRange(session, session.getStartPage(), session.getEndPage()))
                .orElseThrow(() -> new IllegalStateException(
                        "This session has no extracted pages to build a question from."));

        return questionForPage(session, page, difficulty, languageName, provider);
    }

    private java.util.Optional<DocumentPage> firstPageInRange(Session session, int start, int end) {
        return documentPageRepository.findByDocumentIdAndPageNumberBetweenOrderByPageNumberAsc(
                session.getDocument().getId(), start, end).stream().findFirst();
    }

    private String questionForPage(Session session, DocumentPage page, String difficulty,
                                   String languageName, AiProvider provider) {
        String systemPrompt = sessionContextBuilder.buildSystemPrompt(
                session, "[Page " + page.getPageNumber() + "]\n" + page.getExtractedText());
        String instruction = "Write exactly one reading-comprehension question about the page above, "
                + "in " + languageName + ", calibrated to \"" + difficulty + "\" difficulty. "
                + "Reply with only the question itself — no preamble, no quotes, no numbering.";
        return provider.converse(systemPrompt, List.of(), instruction).strip();
    }

    @Override
    public QuizAnswerResponse submitAnswer(Long userId, Long sessionId, SubmitQuizAnswerRequest request) {
        Session session = findOwned(userId, sessionId);
        String languageName = sessionContextBuilder.languageName(session.getLanguage());
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
            String systemPrompt = sessionContextBuilder.buildSystemPrompt(
                    session, "[Page " + page.getPageNumber() + "]\n" + page.getExtractedText());
            String instruction = "Question: " + (request.getQuestion() == null ? "" : request.getQuestion()) + "\n"
                    + "Reader's answer: " + (answer.isBlank() ? "(no answer given)" : answer) + "\n\n"
                    + "Judge this answer against the reading above, calibrated to \"" + difficulty + "\" difficulty "
                    + "(lenient on easy, strict on hard). Reply in " + languageName + " for the feedback sentence. "
                    + "Reply in EXACTLY this three-line format and nothing else:\n"
                    + "CORRECT: yes or no\nSCORE: a number from 0.0 to 1.0\nFEEDBACK: one short encouraging sentence";

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

    /** Parses the AI's CORRECT/SCORE/FEEDBACK reply; degrades gracefully (score 0, raw text as feedback) if the model didn't follow the format. Provider failures now surface as errors upstream rather than reaching here. */
    private GradeResult parseGrade(String response) {
        Matcher correctMatcher = CORRECT_PATTERN.matcher(response);
        Matcher scoreMatcher = SCORE_PATTERN.matcher(response);
        Matcher feedbackMatcher = FEEDBACK_PATTERN.matcher(response);

        boolean correct = correctMatcher.find() && "yes".equalsIgnoreCase(correctMatcher.group(1));
        double score;
        try {
            score = scoreMatcher.find() ? Math.max(0, Math.min(1, Double.parseDouble(scoreMatcher.group(1))))
                    : (correct ? 1.0 : 0.0);
        } catch (NumberFormatException e) {
            score = correct ? 1.0 : 0.0;
        }
        String feedback = feedbackMatcher.find() ? feedbackMatcher.group(1).strip() : response.strip();
        return new GradeResult(correct, score, feedback);
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
