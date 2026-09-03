package com.booki.dto;

import lombok.Data;

import java.util.List;

/**
 * Partial update. A missing field is left unchanged; {@code readerLevel} is the
 * exception — send {@code ""} to clear it (missing/null leaves it as is). Only
 * the editable {@code text} of a slot can change.
 */
@Data
public class UpdateAiProfileRequest {

    private String name;

    /** "beginner" | "intermediate" | "advanced" | "" (clear); null = leave unchanged. */
    private String readerLevel;

    private List<String> enabledCapabilities;

    private List<SlotPatch> slots;

    @Data
    public static class SlotPatch {
        private String key;
        private String text;
    }
}
