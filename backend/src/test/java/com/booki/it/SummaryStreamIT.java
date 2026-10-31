package com.booki.it;

import com.booki.ai.AiProvider;
import com.booki.dto.GenerateSummaryRequest;
import com.booki.dto.MessageRequest;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class SummaryStreamIT extends IntegrationTestBase {

    private ResponseEntity<String> stream(AuthData user, Long sessionId, GenerateSummaryRequest request) {
        return rest.exchange("/api/sessions/" + sessionId + "/summary/stream", HttpMethod.POST,
                new HttpEntity<>(request, auth(user.token())), String.class);
    }

    private GenerateSummaryRequest request(int start, int end, String deliverAs) {
        GenerateSummaryRequest request = new GenerateSummaryRequest();
        request.setStartPage(start);
        request.setEndPage(end);
        request.setDeliverAs(deliverAs);
        return request;
    }

    private List<Map<String, Object>> messages(AuthData user, Long sessionId) {
        return rest.exchange("/api/sessions/" + sessionId + "/messages", HttpMethod.GET,
                new HttpEntity<>(auth(user.token())),
                new ParameterizedTypeReference<List<Map<String, Object>>>() {
                }).getBody();
    }

    @Test
    void writesTheSummaryAsEventsAndSavesItToTheChat() {
        AuthData user = register(uniqueEmail("stream"));
        Long sessionId = createSession(user, uploadThreePageDocument(user), 1, 3);

        ResponseEntity<String> response = stream(user, sessionId, request(1, 3, "chat"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType().toString()).startsWith("text/event-stream");
        assertThat(response.getBody())
                .contains("event: start", "event: delta", "Summary of the pages.", "event: done", "\"cutOff\":false");
        assertThat(messages(user, sessionId).getLast().get("message").toString())
                .startsWith("## Summary (1–3)").contains("Summary of the pages.");
    }

    @Test
    void asPdfTheSummaryStaysInTheChatSoLaterTurnsKnowAboutIt() {
        AuthData user = register(uniqueEmail("stream-pdf"));
        Long sessionId = createSession(user, uploadThreePageDocument(user), 1, 3);

        ResponseEntity<String> response = stream(user, sessionId, request(1, 3, "pdf"));

        assertThat(response.getBody()).contains("event: done", "\"report\":{", "\"type\":\"summary\"");
        assertThat(messages(user, sessionId)).hasSize(1);

        MessageRequest followUp = new MessageRequest();
        followUp.setMessage("Tell me more about that");
        followUp.setPageStart(1);
        followUp.setPageEnd(3);
        rest.exchange("/api/sessions/" + sessionId + "/messages", HttpMethod.POST,
                new HttpEntity<>(followUp, auth(user.token())), String.class);

        assertThat(fakeAi.calls().getLast().context())
                .extracting(AiProvider.Message::content)
                .anyMatch(content -> content.contains("Summary of the pages."));
    }

    @Test
    void aFigureMarkerBecomesAnImageOnlyItsSessionsOwnerCanFetch() {
        AuthData user = register(uniqueEmail("stream-figure"));
        Long sessionId = createSession(user, uploadThreePageDocument(user), 1, 3);
        fakeAi.enqueue("Text\n\n[[FIG:2|A hive]]\n\nMore");

        stream(user, sessionId, request(1, 3, "pdf"));

        String saved = messages(user, sessionId).getLast().get("message").toString();
        Matcher marker = Pattern.compile("\\[\\[FIG:([0-9a-f-]{36})\\|A hive]]").matcher(saved);
        assertThat(marker.find()).isTrue();
        String url = "/api/sessions/" + sessionId + "/figures/" + marker.group(1);

        ResponseEntity<byte[]> image = rest.exchange(url, HttpMethod.GET, new HttpEntity<>(auth(user.token())), byte[].class);
        assertThat(image.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(image.getHeaders().getContentType().toString()).isEqualTo("image/jpeg");
        assertThat(image.getBody()).isNotEmpty();

        AuthData stranger = register(uniqueEmail("stream-stranger"));
        assertThat(rest.exchange(url, HttpMethod.GET, new HttpEntity<>(auth(stranger.token())), String.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void aCutOffReplyIsFlaggedAndKeepsItsNotice() {
        AuthData user = register(uniqueEmail("stream-cut"));
        Long sessionId = createSession(user, uploadThreePageDocument(user), 1, 3);
        fakeAi.enqueue("Half a summ" + AiProvider.CUT_OFF_NOTICE);

        ResponseEntity<String> response = stream(user, sessionId, request(1, 3, "chat"));

        assertThat(response.getBody()).contains("\"cutOff\":true");
        assertThat(AiProvider.wasCutOff(messages(user, sessionId).getLast().get("message").toString())).isTrue();
    }

    @Test
    void aFigureMarkerLeftUnfinishedByACutOffIsRemoved() {
        AuthData user = register(uniqueEmail("stream-cut-marker"));
        Long sessionId = createSession(user, uploadThreePageDocument(user), 1, 3);
        fakeAi.enqueue("Some text\n\n[[FIG:2|A hive that nev" + AiProvider.CUT_OFF_NOTICE);

        stream(user, sessionId, request(1, 3, "chat"));

        String saved = messages(user, sessionId).getLast().get("message").toString();
        assertThat(saved).contains("Some text").doesNotContain("[[FIG");
        assertThat(AiProvider.wasCutOff(saved)).isTrue();
    }

    @Test
    void moreThanFiftyPagesIsAPlainJsonErrorBeforeAnyStreamOrModelCall() {
        AuthData user = register(uniqueEmail("stream-limit"));
        String[] pages = new String[51];
        Arrays.fill(pages, "A page");
        Long sessionId = createSession(user, uploadDocument(user, pdfWithPages(pages), "long.pdf"), 1, 51);
        fakeAi.clear();

        ResponseEntity<String> response = stream(user, sessionId, request(1, 51, "chat"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("up to 50 pages");
        assertThat(fakeAi.calls()).isEmpty();
        assertThat(stream(user, sessionId, request(1, 50, "chat")).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void anotherUsersSessionIsNotFound() {
        AuthData owner = register(uniqueEmail("stream-owner"));
        Long sessionId = createSession(owner, uploadThreePageDocument(owner), 1, 3);

        ResponseEntity<String> response = stream(register(uniqueEmail("stream-other")), sessionId, request(1, 3, "chat"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
