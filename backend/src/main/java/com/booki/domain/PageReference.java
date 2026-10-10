package com.booki.domain;

/**
 * Physical↔editorial page arithmetic for a single constant offset — the
 * physical PDF page on which printed page "1" appears. Deliberately just
 * integer arithmetic: no roman-numeral front matter modeling. See ADR-030.
 */
public final class PageReference {

    private PageReference() {
    }

    public static int toPhysical(int editorialPage, int offset) {
        return editorialPage + offset;
    }

    public static int toEditorial(int physicalPage, int offset) {
        return physicalPage - offset;
    }
}
