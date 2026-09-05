package com.booki.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SubmitQuizAnswerRequest {

    @NotNull
    private Integer pageNumber;

    /** Echoed back verbatim into the stored QuizAttempt for the report. */
    @Size(max = 4000)
    private String question;

    @Size(max = 8000)
    private String answer;

    @Pattern(regexp = "easy|medium|hard")
    private String difficulty;

    private Long aiProfileId;
}
