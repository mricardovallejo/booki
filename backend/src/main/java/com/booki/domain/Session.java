package com.booki.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "sessions")
@Getter
@Setter
@NoArgsConstructor
public class Session {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(nullable = false, name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(nullable = false, name = "document_id")
    private Document document;

    @Column(nullable = false, name = "start_page")
    private Integer startPage;

    @Column(nullable = false, name = "end_page")
    private Integer endPage;

    @Column(nullable = false, name = "current_page")
    private Integer currentPage;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "profile_master_id")
    private ProfileMaster profileMaster;

    @Column(nullable = false, length = 20)
    private String difficulty;

    @Column(nullable = false, length = 10)
    private String language;

    /** Null means "use the app's configured default provider for this profile" — see AiProviderRegistry. */
    @Column(length = 20, name = "ai_provider")
    private String aiProvider;

    @Column(nullable = false, columnDefinition = "TEXT", name = "config_json")
    private String configJson;

    @CreationTimestamp
    @Column(nullable = false, updatable = false, name = "created_at")
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;
}
