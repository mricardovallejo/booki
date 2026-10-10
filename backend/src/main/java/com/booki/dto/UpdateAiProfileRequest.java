package com.booki.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Partial update. A missing field is left unchanged. */
@Data
public class UpdateAiProfileRequest {

    @Size(max = 120)
    private String name;

    @Size(max = 8000)
    private String persona;

    private Boolean isDefault;

    @AssertTrue(message = "isDefault can only be set to true")
    private boolean isDefaultValid() {
        return isDefault == null || isDefault;
    }
}
