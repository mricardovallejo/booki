package com.booki.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class QuizGenerateResponse {
    private List<QuizQuestionResponse> questions;
    private QuizConfigResponse config;
}
