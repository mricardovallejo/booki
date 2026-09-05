package com.booki.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * Partial update. A missing field is left unchanged. Only the editable
 * {@code text} of a slot can change — the locked frame is fixed.
 */
@Data
public class UpdateAiProfileRequest {

    @Size(max = 120)
    private String name;

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
