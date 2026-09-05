package com.booki.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** Body of {@code PATCH /api/sessions/{id}/current-page}. */
@Data
public class UpdateCurrentPageRequest {

    @NotNull
    private Integer currentPage;
}
