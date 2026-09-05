package com.booki.service.impl;

import com.booki.domain.Document;
import com.booki.domain.DocumentPage;
import com.booki.domain.Session;
import com.booki.domain.User;
import com.booki.dto.DocumentResponse;
import com.booki.repository.DocumentPageRepository;
import com.booki.repository.DocumentRepository;
import com.booki.repository.MessageRepository;
import com.booki.repository.QuizAttemptRepository;
import com.booki.repository.SentReportRepository;
import com.booki.repository.SessionRepository;
import com.booki.repository.TagRepository;
import com.booki.repository.UserRepository;
import com.booki.service.DocumentService;
import com.booki.storage.StorageAdapter;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DocumentServiceImpl implements DocumentService {

    private final DocumentRepository documentRepository;
    private final DocumentPageRepository documentPageRepository;
    private final SessionRepository sessionRepository;
    private final MessageRepository messageRepository;
    private final QuizAttemptRepository quizAttemptRepository;
    private final SentReportRepository sentReportRepository;
    private final TagRepository tagRepository;
    private final UserRepository userRepository;
    private final StorageAdapter storage;

    @Override
    @Transactional
    public DocumentResponse uploadDocument(Long userId, MultipartFile file) {
        User user = userRepository.findById(userId).orElseThrow();
        String originalName = file.getOriginalFilename();
        String title = (originalName == null || originalName.isBlank()) ? "document.pdf" : originalName;
        String safeName = title.replaceAll("[^a-zA-Z0-9.-]", "_");
        String key = "documents/" + UUID.randomUUID() + "_" + safeName;

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not read the uploaded file", e);
        }

        // Parse fully before storing anything, so an unreadable upload never
        // leaves an object behind.
        int pageCount;
        List<String> pageTexts;
        try (PDDocument pdDocument = Loader.loadPDF(bytes)) {
            pageCount = pdDocument.getNumberOfPages();
            pageTexts = new ArrayList<>(pageCount);
            PDFTextStripper stripper = new PDFTextStripper();
            for (int i = 1; i <= pageCount; i++) {
                stripper.setStartPage(i);
                stripper.setEndPage(i);
                pageTexts.add(stripper.getText(pdDocument).trim());
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("Invalid or unreadable PDF file", e);
        }

        storage.put(key, bytes, "application/pdf");
        try {
            Document document = new Document();
            document.setUser(user);
            document.setTitle(title);
            document.setFilePath(key);
            document.setPageCount(pageCount);
            documentRepository.save(document);

            for (int i = 0; i < pageCount; i++) {
                DocumentPage page = new DocumentPage();
                page.setDocument(document);
                page.setPageNumber(i + 1);
                page.setExtractedText(pageTexts.get(i));
                documentPageRepository.save(page);
            }
            return toResponse(document);
        } catch (RuntimeException e) {
            // The transaction will roll the rows back; drop the stored object too.
            try {
                storage.delete(key);
            } catch (RuntimeException ignored) {
                // best effort
            }
            throw e;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<DocumentResponse> listDocuments(Long userId) {
        return documentRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public DocumentResponse getDocument(Long userId, Long documentId) {
        return toResponse(findOwned(userId, documentId));
    }

    @Override
    @Transactional(readOnly = true)
    public Resource getDocumentFile(Long userId, Long documentId) {
        Document document = findOwned(userId, documentId);
        return storage.get(document.getFilePath());
    }

    @Override
    @Transactional
    public void deleteDocument(Long userId, Long documentId) {
        Document document = findOwned(userId, documentId);

        tagRepository.removeDocumentFromAllTags(documentId);
        List<Session> sessions = sessionRepository.findByDocumentId(documentId);
        List<Long> sessionIds = sessions.stream().map(Session::getId).toList();
        if (!sessionIds.isEmpty()) {
            messageRepository.deleteBySessionIdIn(sessionIds);
            quizAttemptRepository.deleteBySessionIdIn(sessionIds);
            sentReportRepository.deleteBySessionIdIn(sessionIds);
        }
        sessionRepository.deleteByDocumentId(documentId);
        documentPageRepository.deleteByDocumentId(documentId);
        documentRepository.delete(document);

        try {
            storage.delete(document.getFilePath());
        } catch (RuntimeException ignored) {
            // Object already gone or storage unavailable; the DB rows are the
            // source of truth and they are already deleted.
        }
    }

    private Document findOwned(Long userId, Long documentId) {
        return documentRepository.findByIdAndUserId(documentId, userId)
                .orElseThrow(() -> new NoSuchElementException("Document not found"));
    }

    private DocumentResponse toResponse(Document document) {
        DocumentResponse response = new DocumentResponse();
        response.setId(document.getId());
        response.setTitle(document.getTitle());
        response.setPageCount(document.getPageCount());
        response.setCreatedAt(document.getCreatedAt());
        return response;
    }
}
