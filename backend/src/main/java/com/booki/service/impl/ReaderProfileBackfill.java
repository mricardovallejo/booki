package com.booki.service.impl;

import com.booki.domain.ReaderProfile;
import com.booki.domain.User;
import com.booki.repository.ReaderProfileRepository;
import com.booki.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Registration provisions a new account's owned reader profile and points
 * {@code User.defaultReaderProfile} at it (see {@link ReaderProfileProvisioner}).
 * Accounts created before that existed have no default set — even if they
 * later gained an owned profile some other way, nothing ever pointed the
 * default at it, so they keep resolving to the shipped "General reader"
 * template (see {@code ReaderProfileServiceImpl#effectiveDefault}). On startup,
 * for any user with no default: point it at their first owned profile if they
 * have one, else provision a fresh one. Mirrors {@code AiProfileBackfill}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReaderProfileBackfill {

    private final UserRepository userRepository;
    private final ReaderProfileRepository readerProfileRepository;
    private final ReaderProfileProvisioner provisioner;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void seedMissing() {
        int fixed = 0;
        for (User user : userRepository.findAll()) {
            if (user.getDefaultReaderProfile() != null) {
                continue;
            }
            List<ReaderProfile> owned = readerProfileRepository.findByUserIdOrderByIdAsc(user.getId());
            if (!owned.isEmpty()) {
                user.setDefaultReaderProfile(owned.get(0));
                userRepository.save(user);
            } else {
                provisioner.provisionFor(user);
            }
            fixed++;
        }
        if (fixed > 0) {
            log.info("Fixed default reader profile for {} account(s)", fixed);
        }
    }
}
