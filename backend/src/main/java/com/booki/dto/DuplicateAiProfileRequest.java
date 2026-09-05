package com.booki.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class DuplicateAiProfileRequest {

    /** Defaults to "&lt;source name&gt; (copy)". */
    @Size(max = 120)
    private String name;
}
