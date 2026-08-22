package com.booki.repository;

import com.booki.domain.Session;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SessionRepository extends JpaRepository<Session, Long> {
    List<Session> findByUserIdOrderByCreatedAtDesc(Long userId);
    Optional<Session> findByIdAndUserId(Long id, Long userId);
    List<Session> findByDocumentId(Long documentId);
    void deleteByDocumentId(Long documentId);
}
