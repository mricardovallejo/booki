package com.booki.ai;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Anthropic's Messages API differs from the OpenAI shape in a few ways that
 * matter here: the system prompt is its own top-level field (not a message
 * with role "system"); max_tokens is required rather than optional; and
 * `content` is an array of typed blocks, not always just one "text" block —
 * models with extended thinking put a "thinking" block first, so the reply
 * must be found by type, not assumed to be content[0].
 */
@Slf4j
@Component("claude")
public class ClaudeProvider implements AiProvider, StreamingAiProvider {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    // Generous: extended-thinking models spend some of this on a "thinking" block
    // before the actual reply, and a long summary alone can need a few thousand.
    private static final int MAX_TOKENS = 4096;

    private final WebClient webClient;
    private final String model;

    public ClaudeProvider(@Value("${booki.ai.claude.api-key}") String apiKey,
                          @Value("${booki.ai.claude.model}") String model) {
        this.model = model;
        this.webClient = WebClient.builder()
                .baseUrl("https://api.anthropic.com/v1")
                .defaultHeader("x-api-key", apiKey)
                .defaultHeader("anthropic-version", "2023-06-01")
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    @Override
    public String converse(String systemPrompt, List<Message> context, String userMessage) {
        Map<String, Object> body = requestBody(systemPrompt, context, userMessage, false);

        try {
            String response = webClient.post()
                    .uri("/messages")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            JsonNode root = JSON.readTree(response);
            for (JsonNode block : root.path("content")) {
                if ("text".equals(block.path("type").asString())) {
                    String text = block.path("text").asString();
                    if (text != null && !text.isBlank()) {
                        return text;
                    }
                }
            }
            throw new AiProviderException("claude", "response contained no text block", null);
        } catch (AiProviderException e) {
            throw e;
        } catch (Exception e) {
            log.error("Claude request failed", e);
            throw new AiProviderException("claude", e);
        }
    }

    /**
     * Anthropic streaming: {@code "stream": true} yields SSE events; the reply
     * text arrives as {@code content_block_delta} events of type
     * {@code text_delta}. Runs on the calling thread (blockLast) — the domain
     * layer stays synchronous; only the wire is incremental.
     */
    @Override
    public void converseStream(String systemPrompt, List<Message> context, String userMessage, TokenStream stream) {
        Map<String, Object> body = requestBody(systemPrompt, context, userMessage, true);
        StringBuilder full = new StringBuilder();

        try {
            webClient.post()
                    .uri("/messages")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToFlux(new ParameterizedTypeReference<ServerSentEvent<String>>() { })
                    .doOnNext(event -> handleStreamEvent(event.data(), full, stream))
                    .blockLast();

            String result = full.toString();
            if (result.isBlank()) {
                stream.onError(new AiProviderException("claude", "stream produced no text", null));
                return;
            }
            stream.onComplete(result);
        } catch (AiProviderException e) {
            stream.onError(e);
        } catch (Exception e) {
            log.error("Claude streaming request failed", e);
            stream.onError(new AiProviderException("claude", e));
        }
    }

    private void handleStreamEvent(String data, StringBuilder full, TokenStream stream) {
        if (data == null || data.isBlank()) {
            return;
        }
        JsonNode node = JSON.readTree(data);
        String type = node.path("type").asString();
        if ("content_block_delta".equals(type) && "text_delta".equals(node.path("delta").path("type").asString())) {
            String text = node.path("delta").path("text").asString();
            if (text != null && !text.isEmpty()) {
                full.append(text);
                stream.onDelta(text);
            }
        } else if ("error".equals(type)) {
            throw new AiProviderException("claude", node.path("error").path("message").asString(), null);
        }
    }

    private Map<String, Object> requestBody(String systemPrompt, List<Message> context,
                                            String userMessage, boolean stream) {
        List<Map<String, String>> messages = new ArrayList<>();
        for (Message m : context) {
            messages.add(Map.of("role", m.role(), "content", m.content()));
        }
        messages.add(Map.of("role", "user", "content", userMessage));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("max_tokens", MAX_TOKENS);
        body.put("system", systemPrompt);
        body.put("messages", messages);
        if (stream) {
            body.put("stream", true);
        }
        return body;
    }
}
