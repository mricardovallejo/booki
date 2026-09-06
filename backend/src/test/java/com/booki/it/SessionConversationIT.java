package com.booki.it;

import com.booki.dto.CreateReaderProfileRequest;
import com.booki.dto.AiProfileSummaryResponse;
import com.booki.dto.MessageRequest;
import com.booki.dto.MessageResponse;
import com.booki.dto.ReaderProfileResponse;
import com.booki.dto.SessionContextResponse;
import com.booki.dto.SessionNotificationResponse;
import com.booki.dto.SessionProgressResponse;
import com.booki.dto.SessionRequest;
import com.booki.dto.SessionResponse;
import com.booki.dto.UpdateCurrentPageRequest;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Session lifecycle and the text conversation: creation rules, the shared
 * {@code ConversationEngine} pipeline (history, grounding, capability hints),
 * current page, context inspection, progress and notifications.
 */
class SessionConversationIT extends IntegrationTestBase {

    @Test
    void createSessionAppliesDefaults() {
        AuthData user = register(uniqueEmail("sess"));
        Long documentId = uploadThreePageDocument(user);

        SessionRequest request = new SessionRequest();
        request.setDocumentId(documentId);
        request.setStartPage(1);
        request.setEndPage(3);
        request.setDifficulty("easy");
        request.setLanguage("en");
        request.setAiProvider("fake");
        ResponseEntity<SessionResponse> response = rest.exchange("/api/sessions", HttpMethod.POST,
                new HttpEntity<>(request, auth(user.token())), SessionResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        SessionResponse session = response.getBody();
        assertThat(session.getCurrentPage()).isEqualTo(1);
        assertThat(session.getTitle()).contains("(pages 1-3)");
        assertThat(session.getAiProvider()).isEqualTo("fake");
        assertThat(session.getEnabledCapabilities()).containsExactlyInAnyOrder("explain", "mnemonic", "quiz", "summary");
        assertThat(session.getReaderProfileId()).isNull();
    }

    @Test
    void createSessionRejectsInvalidRangesProviderAndForeignDocuments() {
        AuthData user = register(uniqueEmail("bad-sess"));
        AuthData other = register(uniqueEmail("bad-sess-other"));
        Long documentId = uploadThreePageDocument(user);
        Long foreignDocumentId = uploadThreePageDocument(other);

        assertThat(postSession(user, foreignDocumentId, 1, 3, "fake").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        ResponseEntity<Map<String, String>> inverted = postSession(user, documentId, 3, 1, "fake", errorType());
        assertThat(inverted.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(inverted.getBody().get("error")).isEqualTo("startPage must be less than or equal to endPage");

        ResponseEntity<Map<String, String>> tooWide = postSession(user, documentId, 1, 99, "fake", errorType());
        assertThat(tooWide.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(tooWide.getBody().get("error")).contains("endPage exceeds the document's page count");

        ResponseEntity<Map<String, String>> unknownProvider = postSession(user, documentId, 1, 3, "gpt-99", errorType());
        assertThat(unknownProvider.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(unknownProvider.getBody().get("error")).contains("aiProvider must be one of");
    }

    @Test
    void createSessionLinksExplicitProfiles() {
        AuthData user = register(uniqueEmail("prof-sess"));
        Long documentId = uploadThreePageDocument(user);

        CreateReaderProfileRequest profileRequest = new CreateReaderProfileRequest();
        profileRequest.setName("Young reader");
        profileRequest.setReaderLevel("beginner");
        ResponseEntity<ReaderProfileResponse> profile = rest.exchange("/api/reader-profiles", HttpMethod.POST,
                new HttpEntity<>(profileRequest, auth(user.token())), ReaderProfileResponse.class);
        Long readerProfileId = profile.getBody().id();

        ResponseEntity<List<AiProfileSummaryResponse>> profiles = rest.exchange("/api/ai-profiles", HttpMethod.GET,
                new HttpEntity<>(auth(user.token())), new ParameterizedTypeReference<>() {
                });
        Long studyBuddyId = profiles.getBody().stream()
                .filter(p -> p.name().equals("Study Buddy")).findFirst().orElseThrow().id();

        SessionRequest request = new SessionRequest();
        request.setDocumentId(documentId);
        request.setStartPage(1);
        request.setEndPage(3);
        request.setDifficulty("easy");
        request.setAiProvider("fake");
        request.setReaderProfileId(readerProfileId);
        request.setAiProfileId(studyBuddyId);
        ResponseEntity<SessionResponse> response = rest.exchange("/api/sessions", HttpMethod.POST,
                new HttpEntity<>(request, auth(user.token())), SessionResponse.class);

        assertThat(response.getBody().getReaderProfileId()).isEqualTo(readerProfileId);
        assertThat(response.getBody().getAiProfileId()).isEqualTo(studyBuddyId);
    }

    @Test
    void chatPersistsBothTurnsAndGroundsPromptInDocument() {
        AuthData user = register(uniqueEmail("chat"));
        Long sessionId = createSession(user, uploadThreePageDocument(user), 1, 3);

        MessageRequest message = new MessageRequest();
        message.setMessage("What are these pages about?");
        ResponseEntity<MessageResponse> reply = rest.exchange("/api/sessions/" + sessionId + "/messages", HttpMethod.POST,
                new HttpEntity<>(message, auth(user.token())), MessageResponse.class);

        assertThat(reply.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(reply.getBody().getSpeaker()).isEqualTo("BOOKI");
        assertThat(reply.getBody().getMessage()).isEqualTo("Fake AI reply");

        ResponseEntity<List<MessageResponse>> history = rest.exchange("/api/sessions/" + sessionId + "/messages", HttpMethod.GET,
                new HttpEntity<>(auth(user.token())), new ParameterizedTypeReference<>() {
                });
        assertThat(history.getBody()).hasSize(2);
        assertThat(history.getBody()).extracting(MessageResponse::getSpeaker).containsExactly("USER", "BOOKI");

        FakeAiProvider.Call call = fakeAi.calls().getLast();
        assertThat(call.systemPrompt()).contains("BEGIN DOCUMENT", "Page one text about bees", "Pages 1–3");
        assertThat(call.userMessage()).isEqualTo("What are these pages about?");
    }

    @Test
    void quickActionHintRunsCapabilityWithoutRouting() {
        AuthData user = register(uniqueEmail("hint"));
        Long sessionId = createSession(user, uploadThreePageDocument(user), 1, 3);

        MessageRequest message = new MessageRequest();
        message.setMessage("Ask me something");
        message.setCapabilityHint("quiz");
        ResponseEntity<MessageResponse> reply = rest.exchange("/api/sessions/" + sessionId + "/messages", HttpMethod.POST,
                new HttpEntity<>(message, auth(user.token())), MessageResponse.class);

        assertThat(reply.getBody().getMessage()).isEqualTo("What is the main idea of this page?");
    }

    @Test
    void chatRejectsBadInputTypeAndForeignSessions() {
        AuthData user = register(uniqueEmail("chat-sec"));
        AuthData other = register(uniqueEmail("chat-sec-other"));
        Long sessionId = createSession(user, uploadThreePageDocument(user), 1, 3);

        MessageRequest badType = new MessageRequest();
        badType.setMessage("hi");
        badType.setInputType("VIDEO");
        ResponseEntity<Map<String, String>> invalid = rest.exchange("/api/sessions/" + sessionId + "/messages", HttpMethod.POST,
                new HttpEntity<>(badType, auth(user.token())), errorType());
        assertThat(invalid.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(invalid.getBody().get("error")).isEqualTo("inputType must be TEXT or VOICE");

        MessageRequest ok = new MessageRequest();
        ok.setMessage("hi");
        ResponseEntity<Map<String, String>> foreign = rest.exchange("/api/sessions/" + sessionId + "/messages", HttpMethod.POST,
                new HttpEntity<>(ok, auth(other.token())), errorType());
        assertThat(foreign.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void currentPageFollowsSessionRange() {
        AuthData user = register(uniqueEmail("page"));
        Long sessionId = createSession(user, uploadThreePageDocument(user), 1, 3);

        ResponseEntity<SessionResponse> ok = patchPage(user, sessionId, 2);
        assertThat(ok.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(ok.getBody().getCurrentPage()).isEqualTo(2);

        assertThat(patchPage(user, sessionId, 0).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(patchPage(user, sessionId, 4).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void contextExposesPromptLayersAndResolvedProfiles() {
        AuthData user = register(uniqueEmail("ctx"));
        Long sessionId = createSession(user, uploadThreePageDocument(user), 1, 3);

        ResponseEntity<SessionContextResponse> response = rest.exchange("/api/sessions/" + sessionId + "/context", HttpMethod.GET,
                new HttpEntity<>(auth(user.token())), SessionContextResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        SessionContextResponse context = response.getBody();
        assertThat(context.aiProfileName()).isEqualTo("Patient Tutor");
        assertThat(context.language()).isEqualTo("en");
        assertThat(context.difficulty()).isEqualTo("easy");
        assertThat(context.layers()).extracting(SessionContextResponse.Layer::key)
                .contains("core", "rubric", "persona", "session", "capability_routing");
    }

    @Test
    void progressAndNotificationsReflectActivity() {
        AuthData user = register(uniqueEmail("prog"));
        Long sessionId = createSession(user, uploadThreePageDocument(user), 1, 3);

        ResponseEntity<SessionProgressResponse> initial = rest.exchange("/api/sessions/" + sessionId + "/progress", HttpMethod.GET,
                new HttpEntity<>(auth(user.token())), SessionProgressResponse.class);
        assertThat(initial.getBody().getPagesRead()).isEqualTo(1);
        assertThat(initial.getBody().getTotalPages()).isEqualTo(3);
        assertThat(initial.getBody().getPctRead()).isEqualTo(33);
        assertThat(initial.getBody().getMessageCount()).isZero();

        ResponseEntity<List<SessionNotificationResponse>> initialNotifications = rest.exchange("/api/sessions/" + sessionId + "/notifications", HttpMethod.GET,
                new HttpEntity<>(auth(user.token())), new ParameterizedTypeReference<>() {
                });
        assertThat(initialNotifications.getBody()).extracting(SessionNotificationResponse::getMessage)
                .anyMatch(m -> m.contains("Say hi to BooKI"))
                .anyMatch(m -> m.contains("quiz"));

        MessageRequest message = new MessageRequest();
        message.setMessage("hello there");
        rest.exchange("/api/sessions/" + sessionId + "/messages", HttpMethod.POST,
                new HttpEntity<>(message, auth(user.token())), MessageResponse.class);
        patchPage(user, sessionId, 3);

        ResponseEntity<SessionProgressResponse> after = rest.exchange("/api/sessions/" + sessionId + "/progress", HttpMethod.GET,
                new HttpEntity<>(auth(user.token())), SessionProgressResponse.class);
        assertThat(after.getBody().getMessageCount()).isEqualTo(2);
        assertThat(after.getBody().getPagesRead()).isEqualTo(3);
        assertThat(after.getBody().getPctRead()).isEqualTo(100);

        ResponseEntity<List<SessionNotificationResponse>> doneNotifications = rest.exchange("/api/sessions/" + sessionId + "/notifications", HttpMethod.GET,
                new HttpEntity<>(auth(user.token())), new ParameterizedTypeReference<>() {
                });
        assertThat(doneNotifications.getBody()).extracting(SessionNotificationResponse::getMessage)
                .anyMatch(m -> m.contains("finished"))
                .noneMatch(m -> m.contains("Say hi"));
    }

    private ResponseEntity<SessionResponse> patchPage(AuthData user, Long sessionId, int page) {
        UpdateCurrentPageRequest request = new UpdateCurrentPageRequest();
        request.setCurrentPage(page);
        return rest.exchange("/api/sessions/" + sessionId + "/current-page", HttpMethod.PATCH,
                new HttpEntity<>(request, auth(user.token())), SessionResponse.class);
    }

    private ResponseEntity<SessionResponse> postSession(AuthData user, Long documentId, int start, int end, String provider) {
        SessionRequest request = new SessionRequest();
        request.setDocumentId(documentId);
        request.setStartPage(start);
        request.setEndPage(end);
        request.setDifficulty("easy");
        request.setAiProvider(provider);
        return rest.exchange("/api/sessions", HttpMethod.POST, new HttpEntity<>(request, auth(user.token())), SessionResponse.class);
    }

    private ResponseEntity<Map<String, String>> postSession(AuthData user, Long documentId, int start, int end, String provider, ParameterizedTypeReference<Map<String, String>> type) {
        SessionRequest request = new SessionRequest();
        request.setDocumentId(documentId);
        request.setStartPage(start);
        request.setEndPage(end);
        request.setDifficulty("easy");
        request.setAiProvider(provider);
        return rest.exchange("/api/sessions", HttpMethod.POST, new HttpEntity<>(request, auth(user.token())), type);
    }

    private ParameterizedTypeReference<Map<String, String>> errorType() {
        return new ParameterizedTypeReference<>() {
        };
    }
}
