package com.booki.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ProfileMasterResponse {
    private Long id;
    private String name;
    private String description;
    private String systemPrompt;
}
