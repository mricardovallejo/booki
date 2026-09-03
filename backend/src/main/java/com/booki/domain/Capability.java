package com.booki.domain;

/**
 * A conversational capability a profile can allow. The wire value is
 * {@code name().toLowerCase()} — it must match {@code ConversationCapability.name()}.
 */
public enum Capability {

    QUIZ, SUMMARY, EXPLAIN, MNEMONIC;

    public String wire() {
        return name().toLowerCase();
    }

    public static Capability ofWire(String wire) {
        return valueOf(wire.toUpperCase());
    }
}
