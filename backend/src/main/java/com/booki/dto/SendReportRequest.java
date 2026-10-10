package com.booki.dto;

import com.booki.email.EmailList;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class SendReportRequest {

    /** One address, or several separated by ";" (e.g. a parent and a tutor). */
    @NotBlank
    @Pattern(regexp = EmailList.REGEX)
    private String email;

    /** Only meaningful for the quiz report — which quiz round to report on. Optional; falls back to the session's most recent round. */
    private Long quizRequestId;
}
