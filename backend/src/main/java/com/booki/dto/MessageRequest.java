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

    /**
     * Optional. Set by a quick-action button ("Ask me", "Summarize", …) to run
     * that conversational capability directly. Omitted for normal chat, where
     * the model decides whether a capability applies.
     */
    private String capabilityHint;

    /**
     * The shared activity range (same one Quiz/Summary use) — required on
     * every turn, plain chat included. There is exactly one range concept in
     * BooKI; no per-message override.
     */
    @NotNull
    private Integer pageStart;

    @NotNull
    private Integer pageEnd;
}
