package com.booki.repository;

import com.booki.domain.Message;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MessageRepository extends JpaRepository<Message, Long> {
    List<Message> findBySessionIdOrderByCreatedAtAsc(Long sessionId);
    void deleteBySessionIdIn(List<Long> sessionIds);
}
