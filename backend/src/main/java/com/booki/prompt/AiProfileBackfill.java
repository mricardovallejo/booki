package com.booki.prompt;

import com.booki.repository.AiProfileRepository;
import com.booki.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Keeps shipped templates available without rewriting any user-owned prompt.
 * New users are seeded at registration; on startup, an existing user receives
 * only templates whose {@code basedOnTemplate} key they do not have yet.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiProfileBackfill {

    private final UserRepository userRepository;
    private final AiProfileRepository aiProfileRepository;
    private final SlotPromptCatalog catalog;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void seedMissing() {
        int seededProfiles = 0;
        for (var user : userRepository.findAll()) {
            var existing = aiProfileRepository.findByUserIdOrderByIdAsc(user.getId());
            var existingTemplateKeys = existing.stream()
                    .map(profile -> profile.getBasedOnTemplate())
                    .collect(java.util.stream.Collectors.toSet());
            var missing = catalog.templates().stream()
                    .filter(template -> !existingTemplateKeys.contains(template.key()))
                    .map(template -> {
                        var profile = catalog.newProfile(template, user);
                        if (!existing.isEmpty()) {
                            profile.setDefaultProfile(false);
                        }
                        return profile;
                    })
                    .toList();
            if (!missing.isEmpty()) {
                aiProfileRepository.saveAll(missing);
                seededProfiles += missing.size();
            }
        }
        if (seededProfiles > 0) {
            log.info("Seeded {} missing shipped AI Profile(s)", seededProfiles);
        }
    }
}
