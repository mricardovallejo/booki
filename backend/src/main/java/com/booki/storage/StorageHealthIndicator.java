package com.booki.storage;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Adds a {@code storage} entry to {@code /actuator/health} reporting whether the
 * active {@link StorageAdapter}'s backing store (local directory or S3 bucket)
 * is reachable.
 */
@Component
public class StorageHealthIndicator implements HealthIndicator {

    private final StorageAdapter storage;

    public StorageHealthIndicator(StorageAdapter storage) {
        this.storage = storage;
    }

    @Override
    public Health health() {
        String adapter = storage.getClass().getSimpleName();
        try {
            storage.ping();
            return Health.up().withDetail("adapter", adapter).build();
        } catch (RuntimeException e) {
            return Health.down(e).withDetail("adapter", adapter).build();
        }
    }
}
