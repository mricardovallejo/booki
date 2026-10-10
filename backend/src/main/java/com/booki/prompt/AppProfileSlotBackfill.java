package com.booki.prompt;

import com.booki.repository.AppProfileSlotRepository;
import com.booki.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.stream.Collectors;

/**
 * New users are seeded with their full App Profile (difficulty rubrics,
 * function prompts, capability routing) at registration; on startup, an
 * existing user missing one or more slots receives only the missing ones, and
 * every unedited row (text still equal to its original) picks up the current
 * catalog text. Any already-edited row is left untouched.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AppProfileSlotBackfill {

    private final UserRepository userRepository;
    private final AppProfileSlotRepository appProfileSlotRepository;
    private final SlotPromptCatalog catalog;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void seedMissing() {
        int seeded = 0;
        int updated = 0;
        for (var user : userRepository.findAll()) {
            var existing = appProfileSlotRepository.findByUserId(user.getId());
            for (var row : existing) {
                String current = catalog.appProfileDefaults().get(row.getSlot());
                if (!row.isModified() && current != null && !current.equals(row.getText())) {
                    row.setText(current);
                    row.setOriginalText(current);
                    updated++;
                }
            }
            var existingKeys = existing.stream()
                    .map(r -> r.getSlot())
                    .collect(Collectors.toSet());
            var missing = catalog.newAppProfileSlots(user).stream()
                    .filter(r -> !existingKeys.contains(r.getSlot()))
                    .toList();
            if (!missing.isEmpty()) {
                appProfileSlotRepository.saveAll(missing);
                seeded += missing.size();
            }
        }
        if (seeded + updated > 0) {
            log.info("App Profile sync: {} slot(s) seeded, {} updated to the current catalog", seeded, updated);
        }
    }
}
