package com.booki.dto;

import java.time.Instant;
import java.util.List;

/** An AI Profile with its SlotPrompts. */
public record AiProfileResponse(
        Long id,
        String name,
        boolean isDefault,
        String readerLevel,
        List<String> enabledCapabilities,
        Instant updatedAt,
        List<AiProfileSlotResponse> slots) {
}
