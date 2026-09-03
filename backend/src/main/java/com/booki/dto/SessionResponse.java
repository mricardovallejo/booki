package com.booki.dto;

import lombok.Data;

import java.time.Instant;
import java.util.List;

@Data
public class SessionResponse {
    private Long id;
    private Long documentId;
    private String title;
    private Integer startPage;
    private Integer endPage;
    private Integer currentPage;
    private String difficulty;
    private Long aiProfileId;

    /** Resolved from the session's AI Profile — drives which quick-action buttons show. */
    private List<String> enabledCapabilities;

    private String language;

    /** The resolved provider actually in effect for this session (never null, even if the session didn't pick one explicitly). */
    private String aiProvider;

    private Instant createdAt;
}
