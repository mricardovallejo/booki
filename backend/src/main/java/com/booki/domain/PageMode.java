package com.booki.domain;

/**
 * Whether a page number on an incoming request is the raw physical PDF index,
 * or the printed/editorial number a book's own pagination shows (which often
 * starts later than physical page 1, after front matter). See ADR-030.
 */
public enum PageMode {

    PHYSICAL, EDITORIAL;

    public String wire() {
        return name().toLowerCase();
    }

    /** Null or blank means "physical" — the default everywhere a mode isn't specified. */
    public static PageMode ofWire(String wire) {
        return (wire == null || wire.isBlank()) ? PHYSICAL : valueOf(wire.toUpperCase());
    }
}
