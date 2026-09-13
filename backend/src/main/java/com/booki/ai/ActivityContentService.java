package com.booki.ai;

import com.booki.domain.Document;
import com.booki.storage.StorageAdapter;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * The one place that decides, per document + provider, what an activity
 * (quiz, summary, chat) actually sends the model for a page range. A
 * document-capable provider (Claude, OpenAI) gets a real PDF containing only
 * that range — physically cut from the original, never the whole book, so
 * cost/latency scale with the range size, not the book size; anything else
 * (Kimi, Ollama) falls back to plain text extracted fresh from the same
 * range. Neither path caches anything: slicing/extracting is cheap and local,
 * and re-cutting on every turn is what keeps a stale reference from ever being
 * a concern.
 */
@Component
@RequiredArgsConstructor
public class ActivityContentService {

    private final StorageAdapter storage;

    public ActivityContent resolve(Document document, AiProvider provider, int startPage, int endPage) {
        byte[] fullPdf = readBytes(storage.get(document.getFilePath()));
        if (provider.supportsDocuments()) {
            return new ActivityContent.DocumentReference(slicePdf(fullPdf, startPage, endPage), startPage, endPage);
        }
        return new ActivityContent.PlainText(extractPlainText(fullPdf, startPage, endPage));
    }

    /** Dispatches to whichever {@link AiProvider} call fits the resolved content — the one place callers need. */
    public String converse(AiProvider provider, ActivityContent content, String systemPrompt,
                           List<AiProvider.Message> history, String userMessage) {
        return switch (content) {
            case ActivityContent.DocumentReference doc -> provider.converseWithDocument(
                    systemPrompt, history, userMessage, doc.pdfBytes(), doc.startPage(), doc.endPage());
            case ActivityContent.PlainText ignored -> provider.converse(systemPrompt, history, userMessage);
        };
    }

    /** What to embed in the prompt's "document" section — the real text, or a note pointing at the attached pages. */
    public String documentTextFor(ActivityContent content) {
        return switch (content) {
            case ActivityContent.DocumentReference doc ->
                    "(See the attached document, pages " + doc.startPage() + "-" + doc.endPage() + ".)";
            case ActivityContent.PlainText text -> text.text();
        };
    }

    /** Physically cuts just startPage..endPage into a new, small PDF — the pages this turn actually needs, nothing else. */
    private byte[] slicePdf(byte[] fullPdfBytes, int startPage, int endPage) {
        try (PDDocument source = Loader.loadPDF(fullPdfBytes)) {
            int end = Math.min(endPage, source.getNumberOfPages());
            try (PDDocument sliced = new PDDocument()) {
                for (int i = startPage; i <= end; i++) {
                    sliced.importPage(source.getPage(i - 1));
                }
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                sliced.save(out);
                return out.toByteArray();
            }
        } catch (IOException e) {
            throw new IllegalStateException("Could not slice PDF pages " + startPage + "-" + endPage, e);
        }
    }

    private String extractPlainText(byte[] pdfBytes, int startPage, int endPage) {
        try (PDDocument pdDocument = Loader.loadPDF(pdfBytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setStartPage(startPage);
            stripper.setEndPage(Math.min(endPage, pdDocument.getNumberOfPages()));
            return stripper.getText(pdDocument).trim();
        } catch (IOException e) {
            throw new IllegalStateException("Could not extract text for pages " + startPage + "-" + endPage, e);
        }
    }

    private static byte[] readBytes(Resource resource) {
        try (InputStream in = resource.getInputStream()) {
            return in.readAllBytes();
        } catch (IOException e) {
            throw new IllegalStateException("Could not read stored PDF", e);
        }
    }
}
