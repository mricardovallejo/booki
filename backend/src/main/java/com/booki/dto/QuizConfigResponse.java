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
}
