package com.booki.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class CreateTagRequest {

    @NotBlank
    private String name;

    /** Any id not owned by the caller is silently dropped. */
    private List<Long> documentIds;
}
