package com.booki.prompt;

import com.booki.domain.AiProfile;
import com.booki.domain.User;
import com.booki.repository.AiProfileRepository;
import com.booki.repository.SessionRepository;
import com.booki.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Keeps every account's tutor profiles in step with the shipped templates
 * without rewriting anything a user edited. On startup, per user:
 * <ol>
 *   <li>an unedited copy of a live template picks up its current persona text;</li>
 *   <li>an unedited copy of a retired template (see {@code replaces} in the
 *       catalog) is converted in place into its replacement — same row, so
 *       sessions keep their link — or, when the account already has that
 *       replacement, deleted if no session uses it;</li>
 *   <li>templates the account has no copy of yet are seeded;</li>
 *   <li>if the account's default was converted, the default moves to the
 *       shipped default template. This happens once, at conversion time, so a
 *       default the user picks afterwards is never overridden.</li>
 * </ol>
 * Edited copies (persona text differs from its original) are left untouched.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiProfileBackfill {

    private final UserRepository userRepository;
    private final AiProfileRepository aiProfileRepository;
    private final SessionRepository sessionRepository;
    private final SlotPromptCatalog catalog;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void seedMissing() {
        int updated = 0;
        int converted = 0;
        int removed = 0;
        int seeded = 0;
        for (User user : userRepository.findAll()) {
            List<AiProfile> profiles = new ArrayList<>(aiProfileRepository.findByUserIdOrderByIdAsc(user.getId()));
            Set<String> liveKeys = new HashSet<>();
            profiles.forEach(p -> {
                if (catalog.byKey(p.getBasedOnTemplate()).isPresent()) {
                    liveKeys.add(p.getBasedOnTemplate());
                }
            });
            boolean resetDefault = false;

            for (AiProfile profile : List.copyOf(profiles)) {
                if (isEdited(profile)) {
                    continue;
                }
                var live = catalog.byKey(profile.getBasedOnTemplate());
                if (live.isPresent()) {
                    if (!Objects.equals(profile.getPersonaText(), live.get().personaText())) {
                        applyTemplate(profile, live.get());
                        updated++;
                    }
                    continue;
                }
                var replacement = catalog.replacementFor(profile.getBasedOnTemplate());
                if (replacement.isEmpty()) {
                    continue;
                }
                if (liveKeys.add(replacement.get().key())) {
                    profile.setBasedOnTemplate(replacement.get().key());
                    profile.setName(replacement.get().name());
                    applyTemplate(profile, replacement.get());
                    resetDefault |= profile.isDefaultProfile();
                    converted++;
                } else if (!sessionRepository.existsByAiProfileId(profile.getId())) {
                    resetDefault |= profile.isDefaultProfile();
                    aiProfileRepository.delete(profile);
                    profiles.remove(profile);
                    removed++;
                }
            }

            List<AiProfile> missing = catalog.templates().stream()
                    .filter(template -> !liveKeys.contains(template.key()))
                    .map(template -> {
                        AiProfile profile = catalog.newProfile(template, user);
                        if (!profiles.isEmpty()) {
                            profile.setDefaultProfile(false);
                        }
                        return profile;
                    })
                    .toList();
            if (!missing.isEmpty()) {
                aiProfileRepository.saveAll(missing);
                profiles.addAll(missing);
                seeded += missing.size();
            }

            if (resetDefault) {
                String defaultKey = catalog.defaultTemplate().key();
                profiles.forEach(p -> p.setDefaultProfile(defaultKey.equals(p.getBasedOnTemplate())));
            }
        }
        if (updated + converted + removed + seeded > 0) {
            log.info("AI Profile sync: {} updated, {} converted from retired templates, {} removed, {} seeded",
                    updated, converted, removed, seeded);
        }
    }

    private static boolean isEdited(AiProfile profile) {
        return !Objects.equals(profile.getPersonaText(), profile.getPersonaOriginalText());
    }

    private static void applyTemplate(AiProfile profile, SlotPromptCatalog.Template template) {
        profile.setPersonaText(template.personaText());
        profile.setPersonaOriginalText(template.personaText());
    }
}
