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
    private Integer startPage;
    private Integer endPage;
    private Instant createdAt;
    /** Why the email failed, when {@code simulated} is true because a send was attempted and failed — null when simply "not configured" or not applicable. */
    private String emailError;
}
