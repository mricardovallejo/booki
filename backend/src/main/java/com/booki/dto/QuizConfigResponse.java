package com.booki.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class QuizConfigResponse {
    private Long aiProfileId;
    private String profileName;
    private String difficulty;
    private Integer questionCount;
    private Integer startPage;
    private Integer endPage;
    /** Links this quiz round's answers and report to what was actually requested. See ADR-030. */
    private Long quizRequestId;
}
