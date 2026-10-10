package com.booki.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/** Partial update. A slot missing from the list is left unchanged. */
@Data
public class UpdateAppProfileRequest {

    private List<@Valid SlotPatch> slots;

    @Data
    public static class SlotPatch {

        @Size(max = 40)
        private String key;

        @Size(max = 8000)
        private String text;
    }
}
