package com.booki.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/**
 * Who is reading, in one study context ("Languages", "Sciences", "Philosophy") —
 * goal, prior knowledge, how they like to learn, any accessibility need. Not tied
 * to any {@link AiProfile}: a {@link Session} picks one. A {@code user} of {@code
 * null} marks a shipped read-only reader template everyone sees.
 */
@Entity
@Table(name = "reader_profiles")
@Getter
@Setter
@NoArgsConstructor
public class ReaderProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Null = a shipped template (shared, read-only). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String context = "";

    @Enumerated(EnumType.STRING)
    @Column(length = 20, name = "reader_level")
    private ReaderLevel readerLevel;

    /**
     * Marks the built-in "General reader" as the fallback default. A per-user
     * default is stored on {@link User#getDefaultReaderProfile()}, not here, so
     * this stays false on every user-owned profile.
     */
    @Column(nullable = false, name = "is_default")
    private boolean defaultProfile = false;

    @Column(nullable = false, name = "read_only")
    private boolean readOnly = false;

    @UpdateTimestamp
    @Column(nullable = false, name = "updated_at")
    private Instant updatedAt;
}
