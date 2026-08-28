package com.booki.repository;

import com.booki.domain.Message;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MessageRepository extends JpaRepository<Message, Long> {
    List<Message> findBySessionIdOrderByCreatedAtAsc(Long sessionId);

    /**
     * Newest-first slice of a session's messages, for building the conversation
     * window sent to the model. Callers restore chronological order before use;
     * full history stays intact in persistence.
     */
    List<Message> findBySessionIdOrderByCreatedAtDesc(Long sessionId, Pageable pageable);

    void deleteBySessionIdIn(List<Long> sessionIds);
}
