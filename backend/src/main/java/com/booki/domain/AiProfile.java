package com.booki.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.Objects;

/**
 * The "master" a reading session runs on — just a persona. Who is reading is
 * a separate {@code ReaderProfile}; what a difficulty level means, how
 * BooKI's functions behave, and which capabilities are enabled is all
 * account-wide ({@code AppProfileSlot}, {@code User#enabledCapabilities}),
 * not part of any one tutor profile.
 *
 * <p>Every account is seeded at registration with one copy of each shipped
 * template ({@code SlotPromptCatalog}). {@code personaOriginalText} is the
 * baseline it was seeded with — the profile only remembers, via
 * {@code basedOnTemplate}, which template to "restore to original" against.
 */
@Entity
@Table(name = "ai_profiles")
@Getter
@Setter
@NoArgsConstructor
public class AiProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(nullable = false, name = "user_id")
    private User user;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, length = 40, name = "based_on_template")
    private String basedOnTemplate;

    /** Exactly one of a user's profiles is the create-session default. */
    @Column(nullable = false, name = "is_default")
    private boolean defaultProfile = false;

    @Column(nullable = false, columnDefinition = "TEXT", name = "persona_text")
    private String personaText = "";

    @Column(nullable = false, columnDefinition = "TEXT", name = "persona_original_text")
    private String personaOriginalText = "";

    @UpdateTimestamp
    @Column(nullable = false, name = "updated_at")
    private Instant updatedAt;

    public boolean isPersonaModified() {
        return !Objects.equals(personaText, personaOriginalText);
    }
}
