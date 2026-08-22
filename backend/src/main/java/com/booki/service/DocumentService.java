package com.booki.service;

import com.booki.dto.DocumentResponse;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface DocumentService {
    DocumentResponse uploadDocument(Long userId, MultipartFile file);
    List<DocumentResponse> listDocuments(Long userId);
    DocumentResponse getDocument(Long userId, Long documentId);
    Resource getDocumentFile(Long userId, Long documentId);
    void deleteDocument(Long userId, Long documentId);
}
