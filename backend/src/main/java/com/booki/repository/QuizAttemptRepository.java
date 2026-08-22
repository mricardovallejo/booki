package com.booki.repository;

import com.booki.domain.QuizAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface QuizAttemptRepository extends JpaRepository<QuizAttempt, Long> {
    List<QuizAttempt> findBySessionIdOrderByCreatedAtAsc(Long sessionId);
    long countBySessionId(Long sessionId);
    void deleteBySessionIdIn(List<Long> sessionIds);

    @Modifying
    @Query("UPDATE QuizAttempt q SET q.profileMaster = NULL WHERE q.profileMaster.id = :masterId")
    void clearProfileMaster(Long masterId);
}
