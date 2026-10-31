package com.booki.ai;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.image.JPEGFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Makes the copy of a PDF sent to the model lighter: large images (a book of
 * photographed pages can run to tens of MB) are scaled down and re-encoded. Text
 * and small images are left as they are. The stored original is never touched.
 *
 * <p>Best effort. If anything goes wrong the unshrunk PDF is returned: a heavier
 * request beats a failed one.
 */
@Slf4j
final class PdfImageShrinker {

    /** Keeps normal print legible for the model while cutting a photographed page to about 130 KB. */
    private static final int MAX_SIDE_PX = 1300;
    private static final float JPEG_QUALITY = 0.6f;

    private PdfImageShrinker() {
    }

    static byte[] shrink(byte[] pdf) {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            boolean changed = false;
            for (PDPage page : document.getPages()) {
                changed |= shrinkImagesOn(page, document);
            }
            if (!changed) {
                return pdf;
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            byte[] shrunk = out.toByteArray();
            return shrunk.length < pdf.length ? shrunk : pdf;
        } catch (IOException | RuntimeException e) {
            log.warn("pdf.shrink failed, sending the original pages cause={}", e.toString());
            return pdf;
        }
    }

    private static boolean shrinkImagesOn(PDPage page, PDDocument document) throws IOException {
        PDResources resources = page.getResources();
        if (resources == null) {
            return false;
        }
        boolean changed = false;
        for (COSName name : resources.getXObjectNames()) {
            PDXObject object = resources.getXObject(name);
            // Images with transparency or used as masks would lose it as a JPEG.
            if (object instanceof PDImageXObject image && !image.isStencil() && image.getSoftMask() == null
                    && Math.max(image.getWidth(), image.getHeight()) > MAX_SIDE_PX) {
                BufferedImage scaled = scale(image.getImage());
                resources.put(name, JPEGFactory.createFromImage(document, scaled, JPEG_QUALITY));
                changed = true;
            }
        }
        return changed;
    }

    private static BufferedImage scale(BufferedImage source) {
        double factor = (double) MAX_SIDE_PX / Math.max(source.getWidth(), source.getHeight());
        int width = Math.max(1, (int) (source.getWidth() * factor));
        int height = Math.max(1, (int) (source.getHeight() * factor));
        BufferedImage scaled = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = scaled.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        graphics.drawImage(source, 0, 0, width, height, null);
        graphics.dispose();
        return scaled;
    }
}
