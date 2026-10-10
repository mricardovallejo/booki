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

    /**
     * Default recipient(s) for sent reports — one address, or several
     * separated by ";". An empty string clears it back to the app's global
     * default; null leaves it unchanged. Validated in UserServiceImpl rather
     * than with {@code @Pattern}, since the empty-string "clear" case isn't a
     * valid email itself.
     */
    private String defaultReportEmail;
}
