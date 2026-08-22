package com.booki.ai;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@Conditional(AiProviderOpenAiCondition.class)
public class OpenAiProvider implements AiProvider {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final WebClient webClient;
    private final String model;

    public OpenAiProvider(@Value("${booki.ai.openai.api-key}") String apiKey,
                          @Value("${booki.ai.openai.model}") String model) {
        this.model = model;
        this.webClient = WebClient.builder()
                .baseUrl("https://api.openai.com/v1")
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    @Override
    public String converse(String systemPrompt, List<Message> context, String userMessage) {
        List<Map<String, String>> messages = new java.util.ArrayList<>();
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
            log.error("OpenAI request failed", e);
            return "Lo siento, no pude contactar al asistente en este momento. ¿Puedes intentarlo de nuevo?";
        }
    }
}
