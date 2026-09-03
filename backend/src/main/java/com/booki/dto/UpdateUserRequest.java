package com.booki.dto;

import lombok.Data;

/** Every field is optional — only non-null ones are applied, matching the contract's PATCH semantics. */
@Data
public class UpdateUserRequest {
    private String name;
}
