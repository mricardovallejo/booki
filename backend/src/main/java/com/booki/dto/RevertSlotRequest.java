package com.booki.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RevertSlotRequest {

    /** Wire key of the SlotPrompt to reset, e.g. "rubric_medium". */
    @NotBlank
    private String key;
}
