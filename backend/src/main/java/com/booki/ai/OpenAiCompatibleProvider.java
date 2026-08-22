package com.booki.ai;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.reactive.function.client.WebClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Shared request/response shape for any provider that speaks the OpenAI
 * chat-completions wire format — OpenAI itself, and Kimi/Moonshot (whose
 * API is explicitly OpenAI-compatible under a different base URL/model).
 */
@Slf4j
public abstract class OpenAiCompatibleProvider implements AiProvider {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final WebClient webClient;
    private final String model;

    protected OpenAiCompatibleProvider(String baseUrl, String apiKey, String model) {
        this.model = model;
        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", "application/json")
                .build();
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
                "temperature", 0.7
        );

        try {
            String response = webClient.post()
                    .uri("/chat/completions")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            JsonNode root = JSON.readTree(response);
            return root.path("choices").get(0).path("message").path("content").asString();
        } catch (Exception e) {
            log.error("{} request failed", getClass().getSimpleName(), e);
            return "Sorry, I couldn't reach the assistant right now. Can you try again?";
        }
    }
}
