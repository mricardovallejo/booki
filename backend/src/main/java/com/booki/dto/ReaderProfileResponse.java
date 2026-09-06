package com.booki.dto;

import java.time.Instant;

/** A reader profile. {@code readOnly} is true for shipped, shared templates. */
public record ReaderProfileResponse(
        Long id,
        String name,
        boolean isDefault,
        boolean readOnly,
        String readerLevel,
        String context,
        Instant updatedAt) {
}
