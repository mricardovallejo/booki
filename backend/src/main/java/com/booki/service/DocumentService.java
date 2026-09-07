package com.booki.service;

import com.booki.dto.DocumentResponse;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface DocumentService {
    DocumentResponse uploadDocument(Long userId, MultipartFile file);

    /**
     * Import raw PDF bytes as a document for {@code userId} (parse pages, store
     * the blob, persist the rows) — the shared core behind {@link #uploadDocument}
     * and any server-side seeding. {@code title} is used verbatim.
     */
    DocumentResponse importPdf(Long userId, String title, byte[] pdfBytes);

    List<DocumentResponse> listDocuments(Long userId);
    DocumentResponse getDocument(Long userId, Long documentId);
    Resource getDocumentFile(Long userId, Long documentId);
    void deleteDocument(Long userId, Long documentId);
}
