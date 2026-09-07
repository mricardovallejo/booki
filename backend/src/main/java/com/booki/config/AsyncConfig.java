package com.booki.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Enables {@code @Async} and provides the one small background pool BooKI uses.
 *
 * <p>Currently its only job is best-effort post-registration work (seeding the
 * welcome guide — ADR-022): parsing a bundled PDF and writing it to blob storage
 * must not sit on the sign-up request thread. The pool is deliberately tiny — a
 * burst of sign-ups queues rather than spawning threads — and drains on
 * shutdown so an in-flight seed still completes.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean("backgroundTasks")
    public TaskExecutor backgroundTasks() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(3);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("booki-bg-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(20);
        executor.initialize();
        return executor;
    }
}
