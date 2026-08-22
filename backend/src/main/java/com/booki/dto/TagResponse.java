package com.booki.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.Instant;
import java.util.List;

@Data
@AllArgsConstructor
public class TagResponse {
    private Long id;
    private String name;
    private List<Long> documentIds;
    private Instant createdAt;
}
