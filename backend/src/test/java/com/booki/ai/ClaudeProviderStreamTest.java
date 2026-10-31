package com.booki.ai;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/** Runs the real provider against a local server that speaks Anthropic's streaming format. */
class ClaudeProviderStreamTest {

    private static final String DELTA = "event: content_block_delta\n"
            + "data: {\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\"%s\"}}\n\n";
    private static final String THINKING = "event: content_block_delta\n"
            + "data: {\"type\":\"content_block_delta\",\"delta\":{\"type\":\"thinking_delta\",\"thinking\":\"hmm\"}}\n\n";
    private static final String STOP = "event: message_delta\n"
            + "data: {\"type\":\"message_delta\",\"delta\":{\"stop_reason\":\"%s\"}}\n\n";

    private HttpServer server;
    private final AtomicReference<String> requestBody = new AtomicReference<>();

    private static final class Recording implements StreamingAiProvider.TokenStream {
        final List<String> deltas = new ArrayList<>();
        String completed;
        RuntimeException error;

        @Override
        public void onDelta(String text) {
            deltas.add(text);
        }

        @Override
        public void onComplete(String fullText) {
            completed = fullText;
        }

        @Override
        public void onError(RuntimeException e) {
            error = e;
        }
    }

    private ClaudeProvider providerAnswering(int status, String response) throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/messages", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            exchange.getResponseHeaders().add("Content-Type", status == 200 ? "text/event-stream" : "application/json");
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        return new ClaudeProvider("test-key", "test-model", "http://localhost:" + server.getAddress().getPort());
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    private Recording stream(ClaudeProvider provider) {
        Recording recording = new Recording();
        ActivityContent document = new ActivityContent.DocumentReference(new byte[] {1, 2, 3}, 4, 47);
        provider.converseLongStream("system", List.of(), "summarize", document, recording);
        return recording;
    }

    @Test
    void deliversTextInOrderAndSkipsThinking() throws IOException {
        Recording result = stream(providerAnswering(200,
                THINKING + DELTA.formatted("Hello ") + DELTA.formatted("world") + STOP.formatted("end_turn")));

        assertThat(result.deltas).containsExactly("Hello ", "world");
        assertThat(result.completed).isEqualTo("Hello world");
        assertThat(result.error).isNull();
    }

    @Test
    void attachesTheDocumentAndAsksForALongReply() throws IOException {
        stream(providerAnswering(200, DELTA.formatted("ok") + STOP.formatted("end_turn")));

        assertThat(requestBody.get())
                .contains("\"type\":\"document\"", "\"stream\":true", "\"max_tokens\":32000", "pages 4-47");
    }

    @Test
    void hittingTheTokenLimitIsReportedInTheText() throws IOException {
        Recording result = stream(providerAnswering(200, DELTA.formatted("Half a summ") + STOP.formatted("max_tokens")));

        assertThat(result.deltas).containsExactly("Half a summ", AiProvider.CUT_OFF_NOTICE);
        assertThat(AiProvider.wasCutOff(result.completed)).isTrue();
    }

    @Test
    void aStreamThatEndsWithoutAStopReasonCountsAsCutOff() throws IOException {
        Recording result = stream(providerAnswering(200, DELTA.formatted("Half a summ")));

        assertThat(AiProvider.wasCutOff(result.completed)).isTrue();
    }

    @Test
    void anUpstreamRejectionEndsInOnError() throws IOException {
        Recording result = stream(providerAnswering(401, "{\"error\":{\"message\":\"invalid x-api-key\"}}"));

        assertThat(result.error).isInstanceOf(AiProviderException.class);
        assertThat(result.completed).isNull();
    }
}
