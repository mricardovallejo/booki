package com.booki.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "messages")
@Getter
@Setter
@NoArgsConstructor
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(nullable = false, name = "session_id")
    private Session session;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Speaker speaker;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, name = "input_type")
    private InputType inputType;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    @CreationTimestamp
    @Column(nullable = false, updatable = false, name = "created_at")
    private Instant createdAt;

    public enum Speaker {
        USER, BOOKI
    }

    public enum InputType {
        TEXT, VOICE
    }
}
