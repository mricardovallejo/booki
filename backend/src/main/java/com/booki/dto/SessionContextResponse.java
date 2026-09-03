package com.booki.dto;

import java.util.List;

/**
 * Every part of the instructions BooKI reads before answering a session's turn —
 * not just the editable ones. {@code group} lets a client fold the heavy parts
 * ({@code functions}, {@code routing}) away. Served by {@code GET /sessions/{id}/context}.
 */
public record SessionContextResponse(
        Long aiProfileId,
        String aiProfileName,
        String language,
        String difficulty,
        List<String> enabledCapabilities,
        List<Layer> layers) {

    public record Layer(
            String key,
            String group,
            String label,
            boolean editable,
            String source,
            String content) {
    }
}
