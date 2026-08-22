package com.booki.dto;

import lombok.Data;

import java.time.Instant;

@Data
public class SessionResponse {
    private Long id;
    private Long documentId;
    private String title;
    private Integer startPage;
    private Integer endPage;
    private Integer currentPage;
    private String difficulty;
    private Long profileMasterId;
    private String language;
    private Instant createdAt;
}
