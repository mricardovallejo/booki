package com.booki.service.impl;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Ported from the mock backend's src/reports.js so generated PDFs keep the
 * same layout (BooKI brand header, optional cover block, wrapped sections).
 */
@Component
public class PdfReportBuilder {

    private static final float PAGE_WIDTH = PDRectangle.LETTER.getWidth();
    private static final float PAGE_HEIGHT = PDRectangle.LETTER.getHeight();
    private static final float MARGIN_X = 56;
    private static final float MAX_WIDTH = PAGE_WIDTH - MARGIN_X * 2;
    private static final float LINE_HEIGHT = 15;
    private static final float TOP_Y = PAGE_HEIGHT - 72;

    private static final float[][] COVER_PALETTE = {
            {0.5f, 0.1f, 0.1f},
            {0.45f, 0.28f, 0.05f},
            {0.05f, 0.3f, 0.24f},
            {0.05f, 0.28f, 0.35f},
            {0.28f, 0.1f, 0.4f},
            {0.4f, 0.08f, 0.35f},
            {0.45f, 0.2f, 0.05f},
            {0.15f, 0.12f, 0.4f}
    };

    public record Section(String heading, List<String> lines) {
    }

    public record Cover(float[] color, String initials) {
    }

    public float[] coverColorFor(String title, long id) {
        int hash = 0;
        for (int i = 0; i < title.length(); i++) {
            hash += title.charAt(i);
        }
        hash += (int) id;
        return COVER_PALETTE[Math.floorMod(hash, COVER_PALETTE.length)];
    }

    public String initialsFor(String title) {
        List<String> words = Arrays.stream(title.split("[\\s_.\\-]+"))
                .filter(w -> !w.isBlank())
                .toList();
        if (words.isEmpty()) {
            return "BK";
        }
        if (words.size() == 1) {
            String w = words.get(0);
            return w.substring(0, Math.min(2, w.length())).toUpperCase();
        }
        return ("" + words.get(0).charAt(0) + words.get(1).charAt(0)).toUpperCase();
    }

    public byte[] build(String title, String subtitle, List<Section> sections, Cover cover) {
        try (PDDocument document = new PDDocument()) {
            PDFont helvetica = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            PDFont helveticaBold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            Cursor cursor = new Cursor(document, helvetica, helveticaBold);

            cursor.drawLine("BooKI", 22, true, new Color(0.9f, 0.22f, 0.27f));
            if (cover != null) {
                cursor.drawCover(cover);
            }
            cursor.y -= 26;
            cursor.drawWrapped(title, 14, true, new Color(0.1f, 0.1f, 0.15f));
            if (subtitle != null && !subtitle.isBlank()) {
                cursor.y -= 2;
                cursor.drawWrapped(subtitle, 10, false, new Color(0.4f, 0.4f, 0.45f));
            }
            cursor.y -= 10;

            for (Section section : sections) {
                cursor.ensureSpace(LINE_HEIGHT + 4);
                cursor.drawLine(section.heading(), 12, true, new Color(0.1f, 0.1f, 0.15f));
                for (String line : section.lines()) {
                    cursor.drawWrapped(line, 10, false, new Color(0.25f, 0.25f, 0.32f));
                }
                cursor.y -= 10;
            }

            cursor.close();

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Failed to generate PDF report", e);
        }
    }

    private static final class Cursor {
        private final PDDocument document;
        private final PDFont regular;
        private final PDFont bold;
        private PDPageContentStream stream;
        private float y;

        Cursor(PDDocument document, PDFont regular, PDFont bold) throws IOException {
            this.document = document;
            this.regular = regular;
            this.bold = bold;
            newPage();
        }

        void newPage() throws IOException {
            if (stream != null) {
                stream.close();
            }
            PDPage page = new PDPage(PDRectangle.LETTER);
            document.addPage(page);
            stream = new PDPageContentStream(document, page);
            y = TOP_Y;
        }

        void ensureSpace(float height) throws IOException {
            if (y - height < 60) {
                newPage();
            }
        }

        void drawLine(String text, float size, boolean isBold, Color color) throws IOException {
            ensureSpace(LINE_HEIGHT);
            PDFont font = isBold ? bold : regular;
            stream.beginText();
            stream.setFont(font, size);
            stream.setNonStrokingColor(color);
            stream.newLineAtOffset(MARGIN_X, y);
            stream.showText(text);
            stream.endText();
            y -= LINE_HEIGHT;
        }

        void drawWrapped(String text, float size, boolean isBold, Color color) throws IOException {
            PDFont font = isBold ? bold : regular;
            for (String line : wrap(text, font, size)) {
                ensureSpace(LINE_HEIGHT);
                stream.beginText();
                stream.setFont(font, size);
                stream.setNonStrokingColor(color);
                stream.newLineAtOffset(MARGIN_X, y);
                stream.showText(line);
                stream.endText();
                y -= LINE_HEIGHT;
            }
        }

        void drawCover(Cover cover) throws IOException {
            float size = 54;
            float x = PAGE_WIDTH - MARGIN_X - size;
            float boxY = TOP_Y - size + 16;
            stream.setNonStrokingColor(new Color(cover.color()[0], cover.color()[1], cover.color()[2]));
            stream.addRect(x, boxY, size, size);
            stream.fill();

            PDFont font = bold;
            float textSize = 18;
            float textWidth = font.getStringWidth(cover.initials()) / 1000 * textSize;
            float textX = x + (size - textWidth) / 2;
            float textY = boxY + (size - textSize) / 2 + 4;
            stream.beginText();
            stream.setFont(font, textSize);
            stream.setNonStrokingColor(Color.WHITE);
            stream.newLineAtOffset(textX, textY);
            stream.showText(cover.initials());
            stream.endText();
        }

        void close() throws IOException {
            stream.close();
        }

        private List<String> wrap(String text, PDFont font, float size) throws IOException {
            List<String> lines = new ArrayList<>();
            for (String paragraph : text.split("\n", -1)) {
                if (paragraph.isEmpty()) {
                    lines.add("");
                    continue;
                }
                String[] words = paragraph.split(" ");
                StringBuilder current = new StringBuilder();
                for (String word : words) {
                    String candidate = current.isEmpty() ? word : current + " " + word;
                    if (font.getStringWidth(candidate) / 1000 * size > MAX_WIDTH && !current.isEmpty()) {
                        lines.add(current.toString());
                        current = new StringBuilder(word);
                    } else {
                        current = new StringBuilder(candidate);
                    }
                }
                if (!current.isEmpty()) {
                    lines.add(current.toString());
                }
            }
            return lines;
        }
    }
}
