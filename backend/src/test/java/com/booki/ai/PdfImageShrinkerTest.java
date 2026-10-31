package com.booki.ai;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class PdfImageShrinkerTest {

    private static byte[] pdfWithImage(int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        // Per-pixel noise, like a photo: flat colors would compress better losslessly than as a JPEG.
        Random random = new Random(1);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                image.setRGB(x, y, random.nextInt(0xFFFFFF));
            }
        }
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.drawImage(LosslessFactory.createFromImage(document, image), 0, 0, 612, 792);
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                content.newLineAtOffset(50, 50);
                content.showText("Text stays");
                content.endText();
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        }
    }

    private static PDImageXObject firstImage(PDDocument document) throws IOException {
        var resources = document.getPage(0).getResources();
        return (PDImageXObject) resources.getXObject(resources.getXObjectNames().iterator().next());
    }

    @Test
    void aLargeImageIsScaledDownAndTheTextIsKept() throws IOException {
        byte[] original = pdfWithImage(1700, 2200);

        byte[] shrunk = PdfImageShrinker.shrink(original);

        assertThat(shrunk.length).isLessThan(original.length);
        try (PDDocument document = Loader.loadPDF(shrunk)) {
            assertThat(firstImage(document).getHeight()).isEqualTo(1300);
            assertThat(new PDFTextStripper().getText(document)).contains("Text stays");
        }
    }

    @Test
    void aSmallImageIsLeftAlone() throws IOException {
        byte[] original = pdfWithImage(400, 300);

        assertThat(PdfImageShrinker.shrink(original)).isSameAs(original);
    }

    @Test
    void somethingThatIsNotAPdfComesBackUnchanged() {
        byte[] notAPdf = {1, 2, 3};

        assertThat(PdfImageShrinker.shrink(notAPdf)).isSameAs(notAPdf);
    }
}
