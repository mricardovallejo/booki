package com.booki.dto;

import lombok.Data;

@Data
public class GenerateQuizRequest {
    private Long profileMasterId;
    private String difficulty;
    private Integer questionCount;
}
