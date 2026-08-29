package com.booki.ai;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Talks to a local Ollama daemon — no API key. "Local" here means local to
 * whatever machine runs this backend, not to the end user's device; see
 * docs/backend.md for why that distinction matters for a mobile-first app.
 */
@Slf4j
@Component("ollama")
public class OllamaProvider implements AiProvider {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final WebClient webClient;
    private final String model;

    public OllamaProvider(@Value("${booki.ai.ollama.base-url}") String baseUrl,
                          @Value("${booki.ai.ollama.model}") String model) {
        this.model = model;
        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    @Override
    public String model() {
        return model;
    }

    @Override
    public String converse(String systemPrompt, List<Message> context, String userMessage) {
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemPrompt));
        for (Message m : context) {
            messages.add(Map.of("role", m.role(), "content", m.content()));
        }
        messages.add(Map.of("role", "user", "content", userMessage));

        Map<String, Object> body = Map.of(
                "model", model,
                "messages", messages,
                "stream", false
        );

        try {
            String response = webClient.post()
                    .uri("/api/chat")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            JsonNode root = JSON.readTree(response);
            String content = root.path("message").path("content").asString();
            if (content == null || content.isBlank()) {
                throw new AiProviderException("ollama", "response contained no message content", null);
            }
            return content;
        } catch (AiProviderException e) {
            throw e;
        } catch (Exception e) {
            log.error("Ollama request failed", e);
            throw new AiProviderException("ollama", e);
        }
    }
}
