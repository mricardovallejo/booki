package com.booki.repository;

import com.booki.domain.DocumentPage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DocumentPageRepository extends JpaRepository<DocumentPage, Long> {
    List<DocumentPage> findByDocumentIdAndPageNumberBetweenOrderByPageNumberAsc(
            Long documentId, Integer startPage, Integer endPage);

    List<DocumentPage> findByDocumentIdOrderByPageNumberAsc(Long documentId);

    void deleteByDocumentId(Long documentId);
}
