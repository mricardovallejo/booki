package com.booki.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class GenerateQuizRequest {

    private Long aiProfileId;

    @Pattern(regexp = "easy|medium|hard")
    private String difficulty;

    @Min(1)
    @Max(20)
    private Integer questionCount;
}
