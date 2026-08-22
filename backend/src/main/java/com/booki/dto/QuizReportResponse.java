package com.booki.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class QuizReportResponse {
    private List<QuizAttemptResponse> attempts;
    private Summary summary;

    @Data
    @AllArgsConstructor
    public static class Summary {
        private int total;
        private int correct;
        private int incorrect;
        private int averageScore;
    }
}
