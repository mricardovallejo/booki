package com.booki.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.Instant;
import java.util.List;

@Data
@AllArgsConstructor
public class UserResponse {
    private Long id;
    private String email;
    private String name;
    private Instant createdAt;
    /** Which conversational capabilities (quiz/summary/explain/mnemonic) this account allows — account-wide, not per tutor profile. */
    private List<String> enabledCapabilities;
}
