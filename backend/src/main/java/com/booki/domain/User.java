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
     * The owned reader profile new sessions default to. Registration provisions
     * one; null remains valid after deletion, in which case sessions fall back
     * to the shipped "General reader" template.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "default_reader_profile_id")
    private ReaderProfile defaultReaderProfile;
}
