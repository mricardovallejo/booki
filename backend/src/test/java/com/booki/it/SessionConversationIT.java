package com.booki.it;

import com.booki.dto.CreateReaderProfileRequest;
import com.booki.dto.AiProfileSummaryResponse;
import com.booki.dto.MessageRequest;
import com.booki.dto.MessageResponse;
import com.booki.dto.GenerateQuizRequest;
import com.booki.dto.GenerateSummaryRequest;
import com.booki.dto.QuizGenerateResponse;
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
        request.setDifficulty("easy");
        request.setLanguage("en");
        request.setAiProvider("fake");
        ResponseEntity<SessionResponse> response = rest.exchange("/api/sessions", HttpMethod.POST,
                new HttpEntity<>(request, auth(user.token())), SessionResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        SessionResponse session = response.getBody();
        assertThat(session.getCurrentPage()).isEqualTo(1);
        assertThat(session.getEndPage()).isEqualTo(1);
        assertThat(session.getTitle()).contains("(pages 1-1)");
        assertThat(session.getAiProvider()).isEqualTo("fake");
        assertThat(session.getEnabledCapabilities()).containsExactlyInAnyOrder("explain", "mnemonic", "quiz", "summary");
        assertThat(session.getReaderProfileId()).isNotNull();
    }

    @Test
    void createSessionClampsOutOfRangePagesInsteadOfRejectingThem() {
        // The reading range is no longer locked in at session creation — an
        // out-of-bounds or inverted range is clamped, the same way every other
        // page range in BooKI degrades, not rejected. See ADR-030.
        AuthData user = register(uniqueEmail("bad-sess"));
        AuthData other = register(uniqueEmail("bad-sess-other"));
        Long documentId = uploadThreePageDocument(user);
        Long foreignDocumentId = uploadThreePageDocument(other);

        assertThat(postSession(user, foreignDocumentId, 1, 3, "fake").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        ResponseEntity<SessionResponse> inverted = postSession(user, documentId, 3, 1, "fake");
        assertThat(inverted.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(inverted.getBody().getStartPage()).isEqualTo(1);
        assertThat(inverted.getBody().getEndPage()).isEqualTo(1);

        ResponseEntity<SessionResponse> tooWide = postSession(user, documentId, 1, 99, "fake");
        assertThat(tooWide.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(tooWide.getBody().getStartPage()).isEqualTo(1);
        assertThat(tooWide.getBody().getEndPage()).isEqualTo(3);

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
        Long criticId = profiles.getBody().stream()
                .filter(p -> p.name().equals("Critic")).findFirst().orElseThrow().id();

        SessionRequest request = new SessionRequest();
        request.setDocumentId(documentId);
        request.setStartPage(1);
        request.setEndPage(3);
        request.setDifficulty("easy");
        request.setAiProvider("fake");
        request.setReaderProfileId(readerProfileId);
        request.setAiProfileId(criticId);
        ResponseEntity<SessionResponse> response = rest.exchange("/api/sessions", HttpMethod.POST,
                new HttpEntity<>(request, auth(user.token())), SessionResponse.class);

        assertThat(response.getBody().getReaderProfileId()).isEqualTo(readerProfileId);
        assertThat(response.getBody().getAiProfileId()).isEqualTo(criticId);
    }

    @Test
    void chatPersistsBothTurnsAndGroundsPromptInDocument() {
        AuthData user = register(uniqueEmail("chat"));
        Long sessionId = createSession(user, uploadThreePageDocument(user), 1, 3);

        MessageRequest message = new MessageRequest();
        message.setMessage("What are these pages about?");
        message.setPageStart(1);
        message.setPageEnd(3);
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
        assertThat(call.systemPrompt()).contains(
                "BEGIN DOCUMENT", "Page one text about bees",
                "Reading started on page 1", "furthest page reached is 3");
        assertThat(call.userMessage()).isEqualTo("What are these pages about?");
    }

    @Test
    void quickActionHintRunsCapabilityWithoutRouting() {
        AuthData user = register(uniqueEmail("hint"));
        Long sessionId = createSession(user, uploadThreePageDocument(user), 1, 3);

        MessageRequest message = new MessageRequest();
        message.setMessage("Ask me something");
        message.setCapabilityHint("quiz");
        message.setPageStart(1);
        message.setPageEnd(3);
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
        badType.setPageStart(1);
        badType.setPageEnd(3);
        ResponseEntity<Map<String, String>> invalid = rest.exchange("/api/sessions/" + sessionId + "/messages", HttpMethod.POST,
                new HttpEntity<>(badType, auth(user.token())), errorType());
        assertThat(invalid.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(invalid.getBody().get("error")).isEqualTo("inputType must be TEXT or VOICE");

        MessageRequest ok = new MessageRequest();
        ok.setMessage("hi");
        ok.setPageStart(1);
        ok.setPageEnd(3);
        ResponseEntity<Map<String, String>> foreign = rest.exchange("/api/sessions/" + sessionId + "/messages", HttpMethod.POST,
                new HttpEntity<>(ok, auth(other.token())), errorType());
        assertThat(foreign.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void currentPageCanAdvanceThroughTheDocumentAndExpandsTheReadRange() {
        AuthData user = register(uniqueEmail("page"));
        Long sessionId = createSession(user, uploadThreePageDocument(user), 1, 1);

        ResponseEntity<SessionResponse> ok = patchPage(user, sessionId, 2);
        assertThat(ok.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(ok.getBody().getCurrentPage()).isEqualTo(2);
        assertThat(ok.getBody().getEndPage()).isEqualTo(2);

        ResponseEntity<SessionResponse> lastPage = patchPage(user, sessionId, 3);
        assertThat(lastPage.getBody().getCurrentPage()).isEqualTo(3);
        assertThat(lastPage.getBody().getEndPage()).isEqualTo(3);

        assertThat(patchPage(user, sessionId, 0).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(patchPage(user, sessionId, 4).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void quizAndSummaryAcceptExplicitRangesWithinPagesReadSoFar() {
        AuthData user = register(uniqueEmail("feature-range"));
        Long sessionId = createSession(user, uploadThreePageDocument(user), 1, 1);
        patchPage(user, sessionId, 3);

        MessageRequest conversationalQuiz = new MessageRequest();
        conversationalQuiz.setMessage("Quiz me on pages 2 to 3");
        conversationalQuiz.setCapabilityHint("quiz");
        conversationalQuiz.setPageStart(2);
        conversationalQuiz.setPageEnd(3);
        rest.exchange("/api/sessions/" + sessionId + "/messages", HttpMethod.POST,
                new HttpEntity<>(conversationalQuiz, auth(user.token())), MessageResponse.class);
        assertThat(fakeAi.calls().getLast().systemPrompt())
                .contains("Page two text about hives", "Page three text about honey")
                .doesNotContain("Page one text about bees");

        GenerateQuizRequest quiz = new GenerateQuizRequest();
        quiz.setDifficulty("easy");
        quiz.setQuestionCount(2);
        quiz.setStartPage(2);
        quiz.setEndPage(3);
        ResponseEntity<QuizGenerateResponse> quizResponse = rest.exchange(
                "/api/sessions/" + sessionId + "/quiz", HttpMethod.POST,
                new HttpEntity<>(quiz, auth(user.token())), QuizGenerateResponse.class);
        assertThat(quizResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(quizResponse.getBody().getConfig().getStartPage()).isEqualTo(2);
        assertThat(quizResponse.getBody().getConfig().getEndPage()).isEqualTo(3);
        assertThat(quizResponse.getBody().getQuestions())
                .extracting(q -> q.getPageNumber()).containsExactly(2, 3);

        GenerateSummaryRequest summary = new GenerateSummaryRequest();
        summary.setLengthPages(1);
        summary.setStartPage(2);
        summary.setEndPage(3);
        summary.setDeliverAs("chat");
        ResponseEntity<MessageResponse> summaryResponse = rest.exchange(
                "/api/sessions/" + sessionId + "/summary", HttpMethod.POST,
                new HttpEntity<>(summary, auth(user.token())), MessageResponse.class);
        assertThat(summaryResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(fakeAi.calls().getLast().systemPrompt())
                .contains("Page two text about hives", "Page three text about honey")
                .doesNotContain("Page one text about bees");
    }

    @Test
    void quizReportReflectsTheRequestedRoundNotJustAnsweredAttempts() {
        AuthData user = register(uniqueEmail("quiz-report"));
        Long sessionId = createSession(user, uploadThreePageDocument(user), 1, 1);
        patchPage(user, sessionId, 3);

        GenerateQuizRequest quiz = new GenerateQuizRequest();
        quiz.setDifficulty("hard");
        quiz.setQuestionCount(3);
        quiz.setStartPage(1);
        quiz.setEndPage(3);
        ResponseEntity<QuizGenerateResponse> quizResponse = rest.exchange(
                "/api/sessions/" + sessionId + "/quiz", HttpMethod.POST,
                new HttpEntity<>(quiz, auth(user.token())), QuizGenerateResponse.class);
        Long quizRequestId = quizResponse.getBody().getConfig().getQuizRequestId();
        assertThat(quizRequestId).isNotNull();

        // Only ONE of the three requested questions gets answered — before the
        // fix, the report's page range was derived solely from answered
        // attempts (so it would have shown just this one page instead of the
        // full requested 1-3 range). See ADR-030.
        com.booki.dto.SubmitQuizAnswerRequest answer = new com.booki.dto.SubmitQuizAnswerRequest();
        answer.setPageNumber(2);
        answer.setQuestion("What is on page 2?");
        answer.setAnswer("hives");
        answer.setDifficulty("hard");
        answer.setQuizRequestId(quizRequestId);
        rest.exchange("/api/sessions/" + sessionId + "/quiz/answer", HttpMethod.POST,
                new HttpEntity<>(answer, auth(user.token())), com.booki.dto.QuizAnswerResponse.class);

        // More than one recipient (e.g. a parent and a tutor) — see ADR-030.
        com.booki.dto.SendReportRequest report = new com.booki.dto.SendReportRequest();
        report.setEmail("parent@example.com; tutor@example.com");
        ResponseEntity<com.booki.dto.SentReportResponse> reportResponse = rest.exchange(
                "/api/sessions/" + sessionId + "/reports/quiz", HttpMethod.POST,
                new HttpEntity<>(report, auth(user.token())), com.booki.dto.SentReportResponse.class);

        assertThat(reportResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        com.booki.dto.SentReportResponse body = reportResponse.getBody();
        assertThat(body.getEmail()).isEqualTo("parent@example.com; tutor@example.com");
        assertThat(body.getStartPage()).isEqualTo(1);
        assertThat(body.getEndPage()).isEqualTo(3);
        // No SMTP configured in the test profile — delivery fails, but with a
        // reason now surfaced instead of silently swallowed (ADR-030).
        assertThat(body.isSimulated()).isTrue();
        assertThat(body.getEmailError()).isNotBlank();
    }

    @Test
    void editorialPageModeConvertsUsingTheDocumentsManualOffset() {
        AuthData user = register(uniqueEmail("editorial"));
        Long documentId = uploadThreePageDocument(user);
        Long sessionId = createSession(user, documentId, 1, 1);

        // Printed page 1 is physical page 2 (one page of front matter) — set by
        // hand, same as a user would in the book's settings. See ADR-030.
        com.booki.dto.UpdateDocumentPaginationRequest offset = new com.booki.dto.UpdateDocumentPaginationRequest();
        offset.setOffset(1);
        rest.exchange("/api/documents/" + documentId + "/pagination", HttpMethod.PATCH,
                new HttpEntity<>(offset, auth(user.token())), com.booki.dto.DocumentResponse.class);

        MessageRequest message = new MessageRequest();
        message.setMessage("Explain editorial pages 1 to 2");
        message.setPageStart(1);
        message.setPageEnd(2);
        message.setPageMode("editorial");
        rest.exchange("/api/sessions/" + sessionId + "/messages", HttpMethod.POST,
                new HttpEntity<>(message, auth(user.token())), MessageResponse.class);

        assertThat(fakeAi.calls().getLast().systemPrompt())
                .contains("Page two text about hives", "Page three text about honey")
                .doesNotContain("Page one text about bees");
    }

    @Test
    void contextExposesPromptLayersAndResolvedProfiles() {
        AuthData user = register(uniqueEmail("ctx"));
        Long sessionId = createSession(user, uploadThreePageDocument(user), 1, 1);

        ResponseEntity<SessionContextResponse> response = rest.exchange("/api/sessions/" + sessionId + "/context", HttpMethod.GET,
                new HttpEntity<>(auth(user.token())), SessionContextResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        SessionContextResponse context = response.getBody();
        assertThat(context.aiProfileName()).isEqualTo("BooKI");
        assertThat(context.language()).isEqualTo("en");
        assertThat(context.difficulty()).isEqualTo("easy");
        assertThat(context.layers()).extracting(SessionContextResponse.Layer::key)
                .contains("core", "persona", "session", "capability_routing");
    }

    @Test
    void progressAndNotificationsReflectActivity() {
        AuthData user = register(uniqueEmail("prog"));
        Long sessionId = createSession(user, uploadThreePageDocument(user), 1, 1);

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
        message.setPageStart(1);
        message.setPageEnd(1);
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
