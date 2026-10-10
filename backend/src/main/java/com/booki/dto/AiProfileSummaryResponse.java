package com.booki.dto;

import java.time.Instant;

/** An AI Profile without its persona text (list view). */
public record AiProfileSummaryResponse(
        Long id,
        String name,
        boolean isDefault,
        Instant updatedAt) {
}
