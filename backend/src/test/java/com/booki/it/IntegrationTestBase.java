package com.booki.it;

import com.booki.dto.AuthRequest;
import com.booki.dto.AuthResponse;
import com.booki.dto.DocumentResponse;
import com.booki.dto.SessionRequest;
import com.booki.dto.SessionResponse;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Shared base for the HTTP-level integration tests.
 *
 * <p>One Spring context for the whole {@code com.booki.it} package: real
 * controllers, services, repositories and security on the {@code test} profile
 * (H2 in PostgreSQL mode). The only replaced beans are the outbound
 * <em>boundaries</em> — the AI provider, STT and TTS — because they would
 * otherwise call third-party HTTP APIs. Business logic stays 100% real; there
 * are no Mockito mocks of application classes.
 *
 * <p>Each test uses a freshly registered user (unique email), which isolates
 * data without {@code @DirtiesContext}. Storage writes go to a scratch
 * directory wiped once per test class.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        // Resolve sessions created without an explicit provider to the fake.
        "booki.ai.default-provider=fake",
        // Keep files written by LocalStorageAdapter out of the project root.
        "booki.storage.local-path=build/it-storage"
})
@ActiveProfiles("test")
@Import(IntegrationTestBase.FakeBoundaryBeans.class)
public abstract class IntegrationTestBase {

    @LocalServerPort
    private int port;

    /** Set fresh per test once the random port is known (see {@link #resetFakes()}). */
    protected HttpClient rest;

    @Autowired
    protected FakeAiProvider fakeAi;

    @Autowired
    protected FakeSpeechToTextProvider fakeStt;

    @Autowired
    protected FakeTextToSpeechProvider fakeTts;

    @BeforeAll
    static void wipeScratchStorage() throws IOException {
        Path storage = Path.of("build/it-storage");
        if (Files.exists(storage)) {
            try (Stream<Path> paths = Files.walk(storage)) {
                paths.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(java.io.File::delete);
            }
        }
    }

    @BeforeEach
    void resetFakes() {
        rest = new HttpClient("http://localhost:" + port);
        fakeAi.clear();
        fakeStt.clear();
        fakeTts.clear();
    }

    @TestConfiguration
    static class FakeBoundaryBeans {

        /**
         * Concrete return types on purpose: the beans satisfy the app's
         * interface injection points <em>and</em> the concrete-typed
         * {@code @Autowired} fields the tests use to poke the fakes.
         */

        /** Named "fake" so {@code AiProviderRegistry} exposes it (and the default-provider property resolves it). */
        @Bean("fake")
        FakeAiProvider fakeAiProvider() {
            return new FakeAiProvider();
        }

        @Bean
        @Primary
        FakeSpeechToTextProvider fakeSpeechToText() {
            return new FakeSpeechToTextProvider();
        }

        @Bean
        @Primary
        FakeTextToSpeechProvider fakeTextToSpeech() {
            return new FakeTextToSpeechProvider();
        }
    }

    // ------------------------------------------------------------------
    // Fluent-ish helpers shared by all integration tests
    // ------------------------------------------------------------------

    protected record AuthData(String token, Long userId) {
    }

    protected String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
    }

    protected AuthData register(String email) {
        AuthRequest request = new AuthRequest();
        request.setEmail(email);
        request.setPassword("password123");
        ResponseEntity<AuthResponse> response =
                rest.postForEntity("/api/auth/register", request, AuthResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        return new AuthData(response.getBody().getToken(), response.getBody().getUser().getId());
    }

    protected AuthData register() {
        return register(uniqueEmail("user"));
    }

    protected ResponseEntity<AuthResponse> login(String email, String password) {
        return rest.postForEntity("/api/auth/login", loginRequest(email, password), AuthResponse.class);
    }

    /** Login expecting a non-2xx body (e.g. the {@code {"error": ...}} shape). */
    protected <T> ResponseEntity<T> login(String email, String password, ParameterizedTypeReference<T> type) {
        return rest.exchange("/api/auth/login", HttpMethod.POST,
                new HttpEntity<>(loginRequest(email, password)), type);
    }

    private AuthRequest loginRequest(String email, String password) {
        AuthRequest request = new AuthRequest();
        request.setEmail(email);
        request.setPassword(password);
        return request;
    }

    protected HttpHeaders auth(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    /** Builds a real multi-page PDF in memory (text page markers let tests assert grounding). */
    protected byte[] pdfWithPages(String... pageTexts) {
        try (PDDocument document = new PDDocument()) {
            for (String text : pageTexts) {
                PDPage page = new PDPage();
                document.addPage(page);
                try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                    content.beginText();
                    content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    content.newLineAtOffset(50, 700);
                    content.showText(text);
                    content.endText();
                }
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    protected Long uploadDocument(AuthData user, byte[] pdf, String filename) {
        HttpEntity<MultiValueMap<String, Object>> entity = new HttpEntity<>(multipart("file", pdf, filename,
                MediaType.APPLICATION_PDF), auth(user.token()));
        ResponseEntity<DocumentResponse> response =
                rest.exchange("/api/documents", HttpMethod.POST, entity, DocumentResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        return response.getBody().getId();
    }

    /** Three-page PDF with per-page marker text; the workhorse of the session tests. */
    protected Long uploadThreePageDocument(AuthData user) {
        return uploadDocument(user,
                pdfWithPages("Page one text about bees", "Page two text about hives", "Page three text about honey"),
                "bees.pdf");
    }

    protected Long createSession(AuthData user, Long documentId, int startPage, int endPage) {
        SessionRequest request = new SessionRequest();
        request.setDocumentId(documentId);
        request.setStartPage(startPage);
        request.setEndPage(endPage);
        request.setDifficulty("easy");
        request.setLanguage("en");
        request.setAiProvider("fake");
        HttpEntity<SessionRequest> entity = new HttpEntity<>(request, auth(user.token()));
        ResponseEntity<SessionResponse> response =
                rest.exchange("/api/sessions", HttpMethod.POST, entity, SessionResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        return response.getBody().getId();
    }

    protected MultiValueMap<String, Object> multipart(String field, byte[] bytes, String filename, MediaType mediaType) {
        ByteArrayResource resource = new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return filename;
            }
        };
        HttpHeaders partHeaders = new HttpHeaders();
        partHeaders.setContentType(mediaType);
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add(field, new HttpEntity<>(resource, partHeaders));
        return body;
    }
}
