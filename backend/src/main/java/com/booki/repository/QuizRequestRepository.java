package com.booki.repository;

import com.booki.domain.QuizRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface QuizRequestRepository extends JpaRepository<QuizRequest, Long> {
    Optional<QuizRequest> findByIdAndSessionId(Long id, Long sessionId);

    Optional<QuizRequest> findTopBySessionIdOrderByCreatedAtDesc(Long sessionId);

    void deleteBySessionIdIn(List<Long> sessionIds);
}
