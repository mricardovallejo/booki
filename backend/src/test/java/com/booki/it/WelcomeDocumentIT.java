package com.booki.it;

import com.booki.dto.DocumentResponse;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.TestPropertySource;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The welcome guide (ADR-022) is seeded into a new account asynchronously, on a
 * {@code UserRegisteredEvent} fired after the register transaction commits. The
 * base suite disables it (its tests assert on empty libraries); this class turns
 * it on and checks the guide lands, parsed and readable.
 */
@TestPropertySource(properties = "booki.welcome-document.enabled=true")
class WelcomeDocumentIT extends IntegrationTestBase {

    @Test
    void newAccountGetsTheParsedGuideInItsLibrary() {
        AuthData user = register(uniqueEmail("welcome"));

        DocumentResponse guide = awaitFirstDocument(user);

        assertThat(guide.getTitle()).isEqualTo("Welcome to BooKI · Quick guide");
        assertThat(guide.getPageCount()).isGreaterThan(0);
    }

    private DocumentResponse awaitFirstDocument(AuthData user) {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(15));
        while (Instant.now().isBefore(deadline)) {
            List<DocumentResponse> docs = rest.exchange(
                    "/api/documents", HttpMethod.GET, new HttpEntity<>(auth(user.token())),
                    new ParameterizedTypeReference<List<DocumentResponse>>() {
                    }).getBody();
            if (docs != null && !docs.isEmpty()) {
                return docs.get(0);
            }
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        throw new AssertionError("welcome guide was not seeded within 15s");
    }
}
