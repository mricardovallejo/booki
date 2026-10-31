package com.booki.service.impl;

import com.booki.storage.StorageAdapter;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SummaryPdfRendererTest {

    private static final String FIGURE_ID = "123e4567-e89b-12d3-a456-426614174000";

    private final StorageAdapter storage = mock(StorageAdapter.class);
    private final SummaryPdfRenderer renderer = new SummaryPdfRenderer(storage);

    private static String textOf(byte[] pdf) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(document);
        }
    }

    private static int imagesOnFirstPage(byte[] pdf) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            var resources = document.getPage(0).getResources();
            int count = 0;
            for (var name : resources.getXObjectNames()) {
                if (resources.getXObject(name) instanceof PDImageXObject) {
                    count++;
                }
            }
            return count;
        }
    }

    @Test
    void theLastPageIsNeverBlank() throws IOException {
        for (int lines = 40; lines <= 130; lines++) {
            String notes = "- Une ligne de notes\n".repeat(lines);
            byte[] pdf = renderer.render(7L, "Livre", notes, null);
            try (PDDocument document = Loader.loadPDF(pdf)) {
                PDFTextStripper lastPage = new PDFTextStripper();
                lastPage.setStartPage(document.getNumberOfPages());
                assertThat(lastPage.getText(document)).as("%d lines", lines).contains("Une ligne de notes");
            }
        }
    }

    @Test
    void rendersFormattingAndCharactersTheOldFontCouldNotPrint() throws IOException {
        byte[] pdf = renderer.render(7L, "Le Livre d'Hélène (pages 1-3)", """
                ## La **Renaissance**

                - Léonard → Milan, α et ω
                  - Sous-point

                | Nom | Année |
                |---|---|
                | Dante | 1321 |
                """, new SummaryPdfRenderer.Cover(new float[] {0.4f, 0.2f, 0.1f}, "LL"));

        assertThat(textOf(pdf)).contains("Le Livre d'Hélène (pages 1-3)", "La Renaissance", "Léonard → Milan, α et ω",
                "Sous-point", "Nom", "1321", "LL");
    }

    @Test
    void embedsAFigureWithItsCaption() throws IOException {
        BufferedImage image = new BufferedImage(40, 30, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream jpeg = new ByteArrayOutputStream();
        ImageIO.write(image, "jpeg", jpeg);
        when(storage.get(SummaryFigures.storageKey(7L, FIGURE_ID))).thenReturn(new ByteArrayResource(jpeg.toByteArray()));

        byte[] pdf = renderer.render(7L, "Book", "Intro\n\n[[FIG:" + FIGURE_ID + "|La carte de l'Église à Lyon]]\n\nEnd", null);

        assertThat(imagesOnFirstPage(pdf)).isEqualTo(1);
        assertThat(textOf(pdf)).contains("La carte de l'Église à Lyon", "End").doesNotContain("BOOKIFIGURE", "[[FIG");
    }

    @Test
    void aMissingFigureDoesNotLoseThePdf() throws IOException {
        when(storage.get(anyString())).thenThrow(new IllegalStateException("gone"));

        byte[] pdf = renderer.render(7L, "Book", "[[FIG:" + FIGURE_ID + "|Une carte]]\n\nEnd", null);

        assertThat(textOf(pdf)).contains("End");
    }

    @Test
    void textFromTheModelCannotInjectHtmlOrFetchAnything() throws IOException {
        byte[] pdf = renderer.render(7L, "<b>Book</b> & co", """
                <script>alert(1)</script><img src="http://attacker.test/x.png">

                ![tracker](http://attacker.test/y.png)

                A & B < C
                """, null);

        assertThat(imagesOnFirstPage(pdf)).isZero();
        assertThat(textOf(pdf)).contains("<b>Book</b> & co", "A & B < C");
        verify(storage, never()).get(anyString());
    }
}
