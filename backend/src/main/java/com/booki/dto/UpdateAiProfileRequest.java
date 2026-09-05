package com.booki.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * Partial update. A missing field is left unchanged; {@code readerLevel} is the
 * exception — send {@code ""} to clear it (missing/null leaves it as is). Only
 * the editable {@code text} of a slot can change.
 */
@Data
public class UpdateAiProfileRequest {

    @Size(max = 120)
    private String name;

    /** "beginner" | "intermediate" | "advanced" | "" (clear); null = leave unchanged. */
    @Size(max = 20)
    private String readerLevel;

    private List<String> enabledCapabilities;

    private List<@Valid SlotPatch> slots;

    @Data
    public static class SlotPatch {

        @Size(max = 100)
        private String key;

        @Size(max = 8000)
        private String text;
    }
}
