package com.booki.controller;

import com.booki.dto.GenerateQuizRequest;
import com.booki.dto.QuizAnswerResponse;
import com.booki.dto.QuizGenerateResponse;
import com.booki.dto.QuizReportResponse;
import com.booki.dto.SubmitQuizAnswerRequest;
import com.booki.service.QuizService;
import com.booki.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/sessions/{sessionId}")
@RequiredArgsConstructor
public class QuizController {

    private final QuizService quizService;

    @PostMapping("/quiz")
    public ResponseEntity<QuizGenerateResponse> generateQuiz(@PathVariable Long sessionId,
                                                              @RequestBody(required = false) GenerateQuizRequest request) {
        GenerateQuizRequest body = request != null ? request : new GenerateQuizRequest();
        return ResponseEntity.ok(quizService.generateQuiz(SecurityUtil.currentUserId(), sessionId, body));
    }

    @PostMapping("/quiz/answer")
    public ResponseEntity<QuizAnswerResponse> submitAnswer(@PathVariable Long sessionId,
                                                            @RequestBody SubmitQuizAnswerRequest request) {
        return ResponseEntity.ok(quizService.submitAnswer(SecurityUtil.currentUserId(), sessionId, request));
    }

    @GetMapping("/quiz/attempts")
    public ResponseEntity<QuizReportResponse> getReport(@PathVariable Long sessionId) {
        return ResponseEntity.ok(quizService.getReport(SecurityUtil.currentUserId(), sessionId));
    }
}
