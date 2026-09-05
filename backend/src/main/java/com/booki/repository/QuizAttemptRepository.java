package com.booki.repository;

import com.booki.domain.QuizAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface QuizAttemptRepository extends JpaRepository<QuizAttempt, Long> {
    List<QuizAttempt> findBySessionIdOrderByCreatedAtAsc(Long sessionId);
    long countBySessionId(Long sessionId);

    /** Mean score over a session's attempts, 0 when there are none. */
    @Query("select coalesce(avg(q.score), 0) from QuizAttempt q where q.session.id = :sessionId")
    double averageScoreBySessionId(@Param("sessionId") Long sessionId);

    void deleteBySessionIdIn(List<Long> sessionIds);
}
