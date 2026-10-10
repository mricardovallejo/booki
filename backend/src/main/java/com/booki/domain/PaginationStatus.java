package com.booki.domain;

/**
 * How a {@link Document}'s {@code paginationOffset} (physical page of printed
 * page "1") was established. See ADR-030.
 *
 * <p>{@code MANUAL} always wins: once a user sets the offset by hand, the
 * lazy auto-detect fallback in {@code PageRangeResolver} never touches it
 * again — only the explicit "detect pagination" action can replace it, and
 * only with the user's confirmation on the frontend.
 */
public enum PaginationStatus {
    UNKNOWN, MANUAL, AUTO_DETECTED, DETECTION_FAILED;

    public String wire() {
        return name().toLowerCase();
    }

    public static PaginationStatus ofWire(String wire) {
        return (wire == null || wire.isBlank()) ? UNKNOWN : valueOf(wire.toUpperCase());
    }
}
