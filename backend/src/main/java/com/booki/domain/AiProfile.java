package com.booki.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * The full editable set of prompts a reading session runs on — persona, reader
 * context, difficulty levels, per-function instructions, capability routing —
 * plus the structured {@link ReaderLevel} and enabled {@link Capability} set.
 *
 * <p>Every account is seeded at registration with one copy of each shipped
 * template ({@code SlotPromptCatalog}). A profile is autonomous: it holds its own
 * {@link SlotPrompt} rows and only remembers, via {@code basedOnTemplate}, which
 * template to "restore to original" against.
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

    @Enumerated(EnumType.STRING)
    @Column(length = 20, name = "reader_level")
    private ReaderLevel readerLevel;

    @Convert(converter = CapabilitySetConverter.class)
    @Column(nullable = false, length = 120, name = "enabled_capabilities")
    private Set<Capability> enabledCapabilities = EnumSet.allOf(Capability.class);

    @UpdateTimestamp
    @Column(nullable = false, name = "updated_at")
    private Instant updatedAt;

    @OneToMany(mappedBy = "profile", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SlotPrompt> slots = new ArrayList<>();

    public SlotPrompt slot(SlotKey key) {
        return slots.stream().filter(s -> s.getSlot() == key).findFirst().orElse(null);
    }

    public String text(SlotKey key) {
        SlotPrompt slot = slot(key);
        return slot != null ? slot.getText() : "";
    }

    public void addSlot(SlotPrompt slot) {
        slot.setProfile(this);
        slots.add(slot);
    }
}
