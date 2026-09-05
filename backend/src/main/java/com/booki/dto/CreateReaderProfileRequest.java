package com.booki.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateReaderProfileRequest {

    @NotBlank
    @Size(max = 120)
    private String name;

    @Size(max = 4000)
    private String context;

    @Pattern(regexp = "beginner|intermediate|advanced")
    private String readerLevel;

    /** Copy context / level from this reader profile (defaults to the built-in scaffold). */
    private Long fromId;
}
