package com.booki.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Objects;

/**
 * One prompt inside an {@link AiProfile}. {@code text} is the editable body;
 * {@code originalText} is the text it was seeded with — the baseline for the
 * Edited/Original badge and "restore original text". The locked frame is not
 * stored (see {@link SlotKey}).
 */
@Entity
@Table(name = "ai_profile_slot_prompts",
        uniqueConstraints = @UniqueConstraint(columnNames = {"profile_id", "slot"}))
@Getter
@Setter
@NoArgsConstructor
public class SlotPrompt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(nullable = false, name = "profile_id")
    private AiProfile profile;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private SlotKey slot;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String text;

    @Column(nullable = false, columnDefinition = "TEXT", name = "original_text")
    private String originalText;

    public SlotPrompt(SlotKey slot, String text) {
        this.slot = slot;
        this.text = text;
        this.originalText = text;
    }

    public boolean isModified() {
        return !Objects.equals(text, originalText);
    }
}
