package com.booki.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/** The three layers combined to shape BooKI's answers for a session (see docs/openapi.yaml). */
@Data
@AllArgsConstructor
public class SessionContextResponse {
    private String appPrompt;
    private String masterPrompt;
    private String userPrompt;
}
