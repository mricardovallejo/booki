package com.booki.dto;

import lombok.Data;

import java.time.Instant;

@Data
public class MessageResponse {
    private Long id;
    private String speaker;
    private String inputType;
    private String message;
    private Instant createdAt;
}
