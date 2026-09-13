package com.booki.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
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

    /** The shared activity range — required, same one every other activity uses. */
    @NotNull
    @Min(1)
    private Integer startPage;

    @NotNull
    @Min(1)
    private Integer endPage;
}
