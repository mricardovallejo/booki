package com.booki.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.Instant;

@Data
@AllArgsConstructor
public class SessionNotificationResponse {
    private int id;
    private String type;
    private String message;
    private Instant createdAt;
}
