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

    /** Optional — defaults to page 1. The reading range is no longer fixed at session creation; see ADR-030. */
    @Min(1)
    private Integer startPage;

    @Min(1)
    private Integer endPage;

    /** physical|editorial; null means "physical". Only meaningful alongside startPage/endPage. */
    private String pageMode;

    private Long aiProfileId;

    private Long readerProfileId;

    @NotNull
    private String difficulty;

    /** en|es|fr; defaults to "en" if missing or unrecognized. */
    private String language;

    /** claude|openai|kimi|ollama; null means "use the app's configured default for this profile". */
    private String aiProvider;
}
