package com.booki.dto;

import lombok.Data;

@Data
public class DuplicateAiProfileRequest {

    /** Defaults to "&lt;source name&gt; (copy)". */
    private String name;
}
