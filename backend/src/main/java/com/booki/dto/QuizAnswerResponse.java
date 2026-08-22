package com.booki.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class QuizAnswerResponse {
    private boolean correct;
    private double score;
    private String feedback;
}
