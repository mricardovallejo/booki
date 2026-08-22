package com.booki.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateProfileMasterRequest {

    @NotBlank
    private String name;

    private String description;

    @NotBlank
    private String systemPrompt;
}
