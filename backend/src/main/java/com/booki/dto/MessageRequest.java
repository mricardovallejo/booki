package com.booki.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class MessageRequest {

    @NotBlank
    private String message;

    @NotNull
    private String inputType = "TEXT";
}
