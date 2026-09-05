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
 * null} is the built-in read-only default everyone sees.
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

    /** Null = the built-in default (shared, read-only). */
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

    /** The user's create-session default (the built-in one until they set their own). */
    @Column(nullable = false, name = "is_default")
    private boolean defaultProfile = false;

    @Column(nullable = false, name = "read_only")
    private boolean readOnly = false;

    @UpdateTimestamp
    @Column(nullable = false, name = "updated_at")
    private Instant updatedAt;
}
