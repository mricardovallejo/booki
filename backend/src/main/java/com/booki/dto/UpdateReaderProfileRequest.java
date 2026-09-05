package com.booki.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Partial update. A missing field is left unchanged; {@code readerLevel} takes
 * {@code ""} to clear it. {@code isDefault} may only be set to {@code true}.
 */
@Data
public class UpdateReaderProfileRequest {

    @Size(max = 120)
    private String name;

    @Size(max = 4000)
    private String context;

    /** "beginner" | "intermediate" | "advanced" | "" (clear); null = leave unchanged. */
    @Size(max = 20)
    private String readerLevel;

    private Boolean isDefault;

    @AssertTrue(message = "isDefault can only be set to true")
    private boolean isDefaultValid() {
        return isDefault == null || isDefault;
    }
}
