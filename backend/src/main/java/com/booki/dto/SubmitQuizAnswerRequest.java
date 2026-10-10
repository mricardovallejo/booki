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

    /** Links this answer back to the quiz round it belongs to, for an accurate report. Optional — older clients just degrade to the legacy best-effort report. */
    private Long quizRequestId;
}
