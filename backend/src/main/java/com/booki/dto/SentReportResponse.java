package com.booki.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.Instant;

@Data
@AllArgsConstructor
public class SentReportResponse {
    private Long id;
    private Long sessionId;
    private String type;
    private String email;
    private String downloadUrl;
    private boolean simulated;
    private Instant createdAt;
}
