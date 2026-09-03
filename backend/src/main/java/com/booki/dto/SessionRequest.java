package com.booki.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SessionRequest {

    @NotNull
    private Long documentId;

    private String title;

    @NotNull
    @Min(1)
    private Integer startPage;

    @NotNull
    @Min(1)
    private Integer endPage;

    private Long aiProfileId;

    @NotNull
    private String difficulty;

    /** en|es|fr; defaults to "en" if missing or unrecognized. */
    private String language;

    /** claude|openai|kimi|ollama; null means "use the app's configured default for this profile". */
    private String aiProvider;
}
