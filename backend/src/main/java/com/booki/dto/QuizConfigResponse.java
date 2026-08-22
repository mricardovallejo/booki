package com.booki.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class QuizConfigResponse {
    private Long profileMasterId;
    private String masterName;
    private String difficulty;
    private Integer questionCount;
}
