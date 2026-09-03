package com.booki.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.Instant;

@Data
@AllArgsConstructor
public class QuizAttemptResponse {
    private Long id;
    private Integer pageNumber;
    private String question;
    private String answer;
    private boolean correct;
    private double score;
    private String feedback;
    private String difficulty;
    private String profileName;
    private Instant createdAt;
}
