package com.booki.ai;

import com.booki.domain.Document;
import com.booki.repository.DocumentRepository;
import com.booki.storage.StorageAdapter;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * The one place that decides, per document + provider, what an activity
 * (quiz, summary, chat) actually sends the model for a page range. A
 * document-capable provider (Claude, OpenAI) gets the whole PDF uploaded once
 * and referenced by id forever after; anything else (Kimi, Ollama) falls back
 * to plain text extracted fresh from just the requested pages — never cached,
 * since it is cheap for the small range any one turn asks for.
 */
@Component
@RequiredArgsConstructor
public class ActivityContentService {

    private final DocumentRepository documentRepository;
    private final StorageAdapter storage;

    @Transactional
    public ActivityContent resolve(Document document, AiProvider provider, int startPage, int endPage) {
        if (provider.supportsDocuments()) {
            return new ActivityContent.DocumentReference(ensureUploaded(document, provider), startPage, endPage);
        }
        return new ActivityContent.PlainText(extractPlainText(document, startPage, endPage));
    }

    /** Dispatches to whichever {@link AiProvider} call fits the resolved content — the one place callers need. */
    public String converse(AiProvider provider, ActivityContent content, String systemPrompt,
                           List<AiProvider.Message> history, String userMessage) {
        return switch (content) {
            case ActivityContent.DocumentReference doc -> provider.converseWithDocument(
                    systemPrompt, history, userMessage, doc.fileId(), doc.startPage(), doc.endPage());
            case ActivityContent.PlainText ignored -> provider.converse(systemPrompt, history, userMessage);
        };
    }

    /** What to embed in the prompt's "document" section — the real text, or a note pointing at the attached file. */
    public String documentTextFor(ActivityContent content) {
        return switch (content) {
            case ActivityContent.DocumentReference doc ->
                    "(See the attached document, pages " + doc.startPage() + "-" + doc.endPage() + ".)";
            case ActivityContent.PlainText text -> text.text();
        };
    }

    private String ensureUploaded(Document document, AiProvider provider) {
        String existing = existingFileId(document, provider);
        if (existing != null) {
            return existing;
        }
        byte[] bytes = readBytes(storage.get(document.getFilePath()));
        String fileId = provider.uploadDocument(bytes, document.getTitle());
        rememberFileId(document, provider, fileId);
        documentRepository.save(document);
        return fileId;
    }

    private static String existingFileId(Document document, AiProvider provider) {
        return switch (provider.key()) {
            case "claude" -> document.getClaudeFileId();
            case "openai" -> document.getOpenaiFileId();
            default -> null;
        };
    }

    private static void rememberFileId(Document document, AiProvider provider, String fileId) {
        switch (provider.key()) {
            case "claude" -> document.setClaudeFileId(fileId);
            case "openai" -> document.setOpenaiFileId(fileId);
            default -> throw new IllegalStateException(
                    "Provider '" + provider.key() + "' supports documents but has no file-id field on Document");
        }
    }

    private String extractPlainText(Document document, int startPage, int endPage) {
        byte[] bytes = readBytes(storage.get(document.getFilePath()));
        try (PDDocument pdDocument = Loader.loadPDF(bytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setStartPage(startPage);
            stripper.setEndPage(Math.min(endPage, pdDocument.getNumberOfPages()));
            return stripper.getText(pdDocument).trim();
        } catch (IOException e) {
            throw new IllegalStateException("Could not read stored PDF for document " + document.getId(), e);
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
