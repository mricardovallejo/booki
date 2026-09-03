package com.booki.dto;

import java.time.Instant;
import java.util.List;

/** An AI Profile without its SlotPrompts (list view). */
public record AiProfileSummaryResponse(
        Long id,
        String name,
        boolean isDefault,
        String readerLevel,
        List<String> enabledCapabilities,
        Instant updatedAt) {
}
