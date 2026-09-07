package com.booki.service.impl;

import com.booki.repository.DocumentRepository;
import com.booki.service.DocumentService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Drops a short illustrated "how to use BooKI" guide into every new account's
 * library, so a first-time user has something to open, read and ask BooKI about
 * straight away. The PDF ships as a classpath resource
 * ({@code welcome/booki-guide.pdf}); parsing and storage go through the normal
 * {@link DocumentService#importPdf} path.
 *
 * <p>Best-effort and fully off the request path: {@code AuthController} calls
 * this after {@code register()} has committed, and {@code @Async} then runs the
 * PDF parse + blob write on the {@code backgroundTasks} pool — so it adds
 * nothing to the sign-up response time. Any failure is logged and swallowed;
 * the guide is also reachable from the landing "Learn more" link.
 */
@Component
@RequiredArgsConstructor
public class WelcomeDocumentProvisioner {

    private static final Logger log = LoggerFactory.getLogger(WelcomeDocumentProvisioner.class);
    private static final String RESOURCE = "welcome/booki-guide.pdf";
    static final String TITLE = "Welcome to BooKI · Quick guide";

    private final DocumentService documentService;
    private final DocumentRepository documents;

    @Value("${booki.welcome-document.enabled:true}")
    private boolean enabled;

    @Async("backgroundTasks")
    public void provisionFor(Long userId) {
        if (!enabled || userId == null) {
            return;
        }
        boolean alreadyHasIt = documents.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .anyMatch(d -> TITLE.equals(d.getTitle()));
        if (alreadyHasIt) {
            return;
        }
        try {
            byte[] pdf = new ClassPathResource(RESOURCE).getContentAsByteArray();
            documentService.importPdf(userId, TITLE, pdf);
        } catch (IOException | RuntimeException e) {
            log.warn("Could not add the welcome guide for user {}: {}", userId, e.toString());
        }
    }
}
