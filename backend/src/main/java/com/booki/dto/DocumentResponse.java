package com.booki.dto;

import lombok.Data;

import java.time.Instant;

@Data
public class DocumentResponse {
    private Long id;
    private String title;
    private Integer pageCount;
    private Instant createdAt;
    /** Physical PDF page on which printed/editorial page "1" appears; null if unknown. See ADR-030. */
    private Integer paginationOffset;
    /** unknown|manual|auto_detected|detection_failed. */
    private String paginationStatus;
}
