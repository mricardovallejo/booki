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
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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

    @Value("${booki.storage.pdf-path}")
    private String storagePath;

    @Override
    public DocumentResponse uploadDocument(Long userId, MultipartFile file) {
        User user = userRepository.findById(userId).orElseThrow();
        String originalName = file.getOriginalFilename();
        String title = (originalName == null || originalName.isBlank()) ? "document.pdf" : originalName;
        String safeName = title.replaceAll("[^a-zA-Z0-9.-]", "_");
        String uniqueName = UUID.randomUUID() + "_" + safeName;

        try {
            Path targetDir = Paths.get(storagePath);
            Files.createDirectories(targetDir);
            Path target = targetDir.resolve(uniqueName);
            file.transferTo(target);

            try (PDDocument pdDocument = Loader.loadPDF(target.toFile())) {
                int pageCount = pdDocument.getNumberOfPages();
                Document document = new Document();
                document.setUser(user);
                document.setTitle(title);
                document.setFilePath(target.toString());
                document.setPageCount(pageCount);
                documentRepository.save(document);

                PDFTextStripper stripper = new PDFTextStripper();
                for (int i = 1; i <= pageCount; i++) {
                    stripper.setStartPage(i);
                    stripper.setEndPage(i);
                    String text = stripper.getText(pdDocument).trim();
                    DocumentPage page = new DocumentPage();
                    page.setDocument(document);
                    page.setPageNumber(i);
                    page.setExtractedText(text);
                    documentPageRepository.save(page);
                }

                return toResponse(document);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to process PDF", e);
        }
    }

    @Override
    public List<DocumentResponse> listDocuments(Long userId) {
        return documentRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public DocumentResponse getDocument(Long userId, Long documentId) {
        return toResponse(findOwned(userId, documentId));
    }

    @Override
    public Resource getDocumentFile(Long userId, Long documentId) {
        Document document = findOwned(userId, documentId);
        return new FileSystemResource(Paths.get(document.getFilePath()));
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
            Files.deleteIfExists(Paths.get(document.getFilePath()));
        } catch (IOException ignored) {
            // File already gone or inaccessible; nothing else to do.
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
