package com.booki.repository;

import com.booki.domain.Tag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TagRepository extends JpaRepository<Tag, Long> {
    List<Tag> findByUserId(Long userId);
    Optional<Tag> findByIdAndUserId(Long id, Long userId);

    /**
     * Bulk-removes a document from every tag's membership, bypassing the
     * Java collection so it works even when no Tag entities are loaded —
     * used by DocumentService when a document is deleted.
     */
    @Modifying
    @Query(value = "DELETE FROM tag_documents WHERE document_id = :documentId", nativeQuery = true)
    void removeDocumentFromAllTags(@Param("documentId") Long documentId);
}
