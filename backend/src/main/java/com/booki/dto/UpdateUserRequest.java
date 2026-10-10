package com.booki.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/** Every field is optional — only non-null ones are applied, matching the contract's PATCH semantics. */
@Data
public class UpdateUserRequest {

    @Size(max = 120)
    private String name;

    private List<String> enabledCapabilities;
}
