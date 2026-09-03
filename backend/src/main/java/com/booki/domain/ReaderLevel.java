package com.booki.domain;

/** Self-assessed reader level on an AI Profile — used to suggest a session difficulty. */
public enum ReaderLevel {

    BEGINNER, INTERMEDIATE, ADVANCED;

    public String wire() {
        return name().toLowerCase();
    }

    public static ReaderLevel ofWire(String wire) {
        return (wire == null || wire.isBlank()) ? null : valueOf(wire.toUpperCase());
    }
}
