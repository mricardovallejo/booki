package com.booki.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SubmitQuizAnswerRequest {

    @NotNull
    private Integer pageNumber;

    /** Echoed back verbatim into the stored QuizAttempt for the report. */
    private String question;

    private String answer;

    private String difficulty;

    private Long profileMasterId;
}
