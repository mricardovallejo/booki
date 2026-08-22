package com.booki.repository;

import com.booki.domain.SentReport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SentReportRepository extends JpaRepository<SentReport, Long> {
    List<SentReport> findBySessionIdOrderByCreatedAtDesc(Long sessionId);
    Optional<SentReport> findByIdAndSessionUserId(Long id, Long userId);
    void deleteBySessionIdIn(List<Long> sessionIds);
}
