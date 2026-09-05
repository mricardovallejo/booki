package com.booki.dto;

import java.time.Instant;

/** A reader profile. {@code readOnly} is true only for the built-in "General reader". */
public record ReaderProfileResponse(
        Long id,
        String name,
        boolean isDefault,
        boolean readOnly,
        String readerLevel,
        String context,
        Instant updatedAt) {
}
