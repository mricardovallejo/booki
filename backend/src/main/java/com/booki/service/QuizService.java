package com.booki.service;

import com.booki.dto.GenerateQuizRequest;
import com.booki.dto.QuizAnswerResponse;
import com.booki.dto.QuizGenerateResponse;
import com.booki.dto.QuizReportResponse;
import com.booki.dto.SubmitQuizAnswerRequest;

public interface QuizService {
    QuizGenerateResponse generateQuiz(Long userId, Long sessionId, GenerateQuizRequest request);
    QuizAnswerResponse submitAnswer(Long userId, Long sessionId, SubmitQuizAnswerRequest request);
    QuizReportResponse getReport(Long userId, Long sessionId);
}
