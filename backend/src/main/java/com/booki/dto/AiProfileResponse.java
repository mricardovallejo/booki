package com.booki.dto;

import java.time.Instant;

/**
 * An AI Profile: a name and its persona. {@code personaOriginal} is the
 * shipped baseline for the Edited/Original badge and "restore original
 * text". Enabled capabilities, difficulty rubrics, and function prompts are
 * account-wide — see {@code UserResponse} and the App Profile — not part of
 * any one tutor profile.
 */
public record AiProfileResponse(
        Long id,
        String name,
        boolean isDefault,
        Instant updatedAt,
        String persona,
        String personaOriginal,
        boolean personaModified) {
}
