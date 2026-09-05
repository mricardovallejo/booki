package com.booki.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SendReportRequest {

    @NotBlank
    @Email
    private String email;
}
