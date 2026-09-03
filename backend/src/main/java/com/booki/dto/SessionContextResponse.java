package com.booki.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/** What shapes BooKI's answers for a session — replaced by the layered form in Stage 3. */
@Data
@AllArgsConstructor
public class SessionContextResponse {
    private String appPrompt;
    private String personaPrompt;
    private String readerContext;
}
