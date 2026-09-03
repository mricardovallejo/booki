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
 * Safety net: any user with no AI Profiles (e.g. registered before this feature,
 * or against a database that wasn't wiped) gets the seeded set on next startup.
 * New users are seeded at registration; this is idempotent.
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
        int seeded = 0;
        for (var user : userRepository.findAll()) {
            if (aiProfileRepository.countByUserId(user.getId()) == 0) {
                aiProfileRepository.saveAll(catalog.seedFor(user));
                seeded++;
            }
        }
        if (seeded > 0) {
            log.info("Seeded AI Profiles for {} user(s) with none", seeded);
        }
    }
}
