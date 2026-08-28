package com.booki.service;

import com.booki.domain.Session;
import com.booki.dto.GenerateQuizRequest;
import com.booki.dto.QuizAnswerResponse;
import com.booki.dto.QuizGenerateResponse;
import com.booki.dto.QuizReportResponse;
import com.booki.dto.SubmitQuizAnswerRequest;

public interface QuizService {
    QuizGenerateResponse generateQuiz(Long userId, Long sessionId, GenerateQuizRequest request);
    QuizAnswerResponse submitAnswer(Long userId, Long sessionId, SubmitQuizAnswerRequest request);
    QuizReportResponse getReport(Long userId, Long sessionId);

    /**
     * One comprehension question about the reader's current page, grounded in
     * the same page text and three-layer prompt the full quiz uses. For the
     * conversational quiz capability — does not persist a {@code QuizAttempt};
     * the scored flow stays behind {@link #submitAnswer}.
     */
    String generateComprehensionQuestion(Session session);
}
