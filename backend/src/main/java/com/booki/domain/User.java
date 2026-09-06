package com.booki.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false, name = "password_hash")
    private String passwordHash;

    @Column(nullable = false)
    private String name;

    @CreationTimestamp
    @Column(nullable = false, updatable = false, name = "created_at")
    private Instant createdAt;

    /**
     * The reader profile new sessions default to. Null until the user makes their
     * first own reader profile (or explicitly picks one) — sessions fall back to
     * the built-in "General reader" while it is null.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "default_reader_profile_id")
    private ReaderProfile defaultReaderProfile;
}
