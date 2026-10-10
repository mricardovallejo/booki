package com.booki.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Objects;

/**
 * One row of the account-wide "App Profile": a difficulty rubric, a function
 * prompt, or the capability-routing instruction — see {@link AppProfileSlotKey}.
 * {@code originalText} is the shipped default it was seeded with, the
 * baseline for the Edited/Original badge and "restore original text".
 */
@Entity
@Table(name = "app_profile_slots", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "slot"}))
@Getter
@Setter
@NoArgsConstructor
public class AppProfileSlot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(nullable = false, name = "user_id")
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private AppProfileSlotKey slot;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String text;

    @Column(nullable = false, columnDefinition = "TEXT", name = "original_text")
    private String originalText;

    public AppProfileSlot(User user, AppProfileSlotKey slot, String text) {
        this.user = user;
        this.slot = slot;
        this.text = text;
        this.originalText = text;
    }

    public boolean isModified() {
        return !Objects.equals(text, originalText);
    }
}
