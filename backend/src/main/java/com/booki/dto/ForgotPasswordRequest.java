package com.booki.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ForgotPasswordRequest {
    @NotBlank
    @Email
    private String email;

    /** Where to send the link; defaults to the account's email. Anything else must be allowlisted (ADR-033). */
    @Email
    private String deliverTo;
}
