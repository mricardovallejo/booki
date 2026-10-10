package com.booki.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * One generated quiz round, written at generation time — before any answer
 * comes in — so a report can always say what was actually requested (range,
 * question count, difficulty), not just what got answered. See ADR-030.
 */
@Entity
@Table(name = "quiz_requests")
@Getter
@Setter
@NoArgsConstructor
public class QuizRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(nullable = false, name = "session_id")
    private Session session;

    @Column(nullable = false, name = "start_page")
    private Integer startPage;

    @Column(nullable = false, name = "end_page")
    private Integer endPage;

    @Column(nullable = false, length = 20)
    private String difficulty;

    @Column(nullable = false, name = "question_count")
    private Integer questionCount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, name = "page_mode")
    private PageMode pageMode = PageMode.PHYSICAL;

    @CreationTimestamp
    @Column(nullable = false, updatable = false, name = "created_at")
    private Instant createdAt;
}
