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
     * Optional page range for a quick-action capability (quiz / summary / explain
     * / mnemonic). The frontend sends the shared activity range with these; plain
     * chat leaves them null and the turn stays anchored on the reading position.
     */
    private Integer pageStart;
    private Integer pageEnd;
}
