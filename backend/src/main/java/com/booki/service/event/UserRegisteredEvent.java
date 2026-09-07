package com.booki.service.event;

/**
 * Published by {@code AuthService} once a new account and its default profiles
 * are committed. Post-registration side effects that must not sit on the sign-up
 * request thread (currently only the welcome-guide seeding) listen for it with
 * {@code @TransactionalEventListener(AFTER_COMMIT)}.
 */
public record UserRegisteredEvent(Long userId) {
}
