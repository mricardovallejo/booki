package com.booki.service.impl;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Regression test for the ".notdef" crash: AI-generated text can contain a
 * character the base-14 Helvetica font can't draw (e.g. a phonetic symbol like
 * U+1D49), which used to abort the whole report. French "œ" must still render
 * as itself, not get caught by an overly broad filter — see PdfReportBuilder#sanitizeForPdf.
 */
class PdfReportBuilderTest {

    private final PdfReportBuilder builder = new PdfReportBuilder();

    @Test
    void neverThrowsOnACharacterTheFontCannotEncode() {
        String withUnencodableChar = "The reader's answer used ᵉ correctly.";
        assertThatCode(() -> builder.build("Report", null,
                List.of(new PdfReportBuilder.Section("Feedback", List.of(withUnencodableChar))), null))
                .doesNotThrowAnyException();
    }

    @Test
    void preservesFrenchLettersTheFontActuallySupports() throws IOException {
        String french = "Le cœur et la sœur travaillent ensemble.";
        byte[] pdf = builder.build("Rapport", null,
                List.of(new PdfReportBuilder.Section("Résumé", List.of(french))), null);

        String extracted = extractText(pdf);
        assertThat(extracted).contains("cœur", "sœur");
    }

    @Test
    void substitutesOnlyTheUnsupportedCharacterNotTheWholeLine() throws IOException {
        String mixed = "Valid French: œ, but not this: ᵉ.";
        byte[] pdf = builder.build("Report", null,
                List.of(new PdfReportBuilder.Section("Section", List.of(mixed))), null);

        String extracted = extractText(pdf);
        assertThat(extracted).contains("œ").doesNotContain("ᵉ");
    }

    private static String extractText(byte[] pdf) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(document);
        }
    }
}
