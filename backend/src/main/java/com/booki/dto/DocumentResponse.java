package com.booki.dto;

import lombok.Data;

import java.time.Instant;

@Data
public class DocumentResponse {
    private Long id;
    private String title;
    private Integer pageCount;
    private Instant createdAt;
}
