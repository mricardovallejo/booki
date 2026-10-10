package com.booki.dto;

import lombok.Data;

/** {@code offset == null} clears the pagination offset back to UNKNOWN. */
@Data
public class UpdateDocumentPaginationRequest {
    private Integer offset;
}
