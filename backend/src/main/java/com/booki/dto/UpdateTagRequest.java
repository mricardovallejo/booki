package com.booki.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateTagRequest {

    @NotBlank
    @Size(max = 80)
    private String name;
}
