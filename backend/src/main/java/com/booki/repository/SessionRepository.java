package com.booki.repository;

import com.booki.domain.Session;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface SessionRepository extends JpaRepository<Session, Long> {
    List<Session> findByUserIdOrderByCreatedAtDesc(Long userId);
    Optional<Session> findByIdAndUserId(Long id, Long userId);
    List<Session> findByDocumentId(Long documentId);
    void deleteByDocumentId(Long documentId);

    @Modifying
    @Query("UPDATE Session s SET s.profileMaster = NULL WHERE s.profileMaster.id = :masterId")
    void clearProfileMaster(Long masterId);
}
