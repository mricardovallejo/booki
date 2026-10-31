package com.booki.ai;

import com.booki.config.OutboundHttp;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
    /** Room for a condensed summary of many pages. Only safe when streaming: a blocking call this long would time out. */
    private static final int LONG_STREAM_MAX_TOKENS = 32000;
    /** How the API says a reply stopped before the model finished. */
    private static final Set<String> CUT_OFF_STOP_REASONS = Set.of("max_tokens", "refusal");

    private final WebClient webClient;
    private final String model;

    @Autowired
    public ClaudeProvider(@Value("${booki.ai.claude.api-key}") String apiKey,
                          @Value("${booki.ai.claude.model}") String model) {
        this(apiKey, model, "https://api.anthropic.com/v1");
    }

    ClaudeProvider(String apiKey, String model, String baseUrl) {
        this.model = model;
        this.webClient = WebClient.builder()
                .clientConnector(OutboundHttp.connector())
                .baseUrl(baseUrl)
                .defaultHeader("x-api-key", apiKey)
                .defaultHeader("anthropic-version", "2023-06-01")
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    @Override
    public String model() {
        return model;
    }

    @Override
    public String key() {
        return "claude";
    }

    @Override
    public boolean supportsDocuments() {
        return true;
    }

    /**
     * Same request shape as {@link #converse}, except the final user message's
     * content becomes a two-block array: the PDF inline as base64 (see
     * docs/build-with-claude/pdf-support — no Files API, no upload step;
     * {@code pdfBytes} is only ever the small slice of pages this turn needs,
     * physically cut by {@code ActivityContentService}, never the whole book)
     * plus the text instruction.
     */
    @Override
    public String converseWithDocument(String systemPrompt, List<Message> context, String userMessage,
                                        byte[] pdfBytes, int startPage, int endPage) {
        Map<String, Object> body = documentRequestBody(systemPrompt, context, userMessage, pdfBytes, startPage, endPage);

        try {
            String response = webClient.post()
                    .uri("/messages")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(OutboundHttp.CALL_TIMEOUT)
                    .block();
            return replyText(JSON.readTree(response));
        } catch (AiProviderException e) {
            throw e;
        } catch (Exception e) {
            log.error("Claude document request failed", e);
            throw new AiProviderException("claude", e);
        }
    }

    @Override
    public boolean supportsImages() {
        return true;
    }

    @Override
    public String describeImage(String prompt, byte[] jpeg) {
        Map<String, Object> imageBlock = Map.of(
                "type", "image",
                "source", Map.of(
                        "type", "base64",
                        "media_type", "image/jpeg",
                        "data", Base64.getEncoder().encodeToString(jpeg)));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("max_tokens", MAX_TOKENS);
        body.put("messages", List.of(Map.of(
                "role", "user",
                "content", List.of(imageBlock, Map.of("type", "text", "text", prompt)))));
        try {
            String response = webClient.post()
                    .uri("/messages")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(OutboundHttp.CALL_TIMEOUT)
                    .block();
            return replyText(JSON.readTree(response));
        } catch (AiProviderException e) {
            throw e;
        } catch (Exception e) {
            throw new AiProviderException("claude", e);
        }
    }

    private Map<String, Object> documentRequestBody(String systemPrompt, List<Message> context, String userMessage,
                                                    byte[] pdfBytes, int startPage, int endPage) {
        List<Map<String, String>> historyMessages = new ArrayList<>();
        for (Message m : context) {
            historyMessages.add(Map.of("role", m.role(), "content", m.content()));
        }

        Map<String, Object> documentBlock = Map.of(
                "type", "document",
                "source", Map.of(
                        "type", "base64",
                        "media_type", "application/pdf",
                        "data", Base64.getEncoder().encodeToString(pdfBytes)));
        Map<String, Object> textBlock = Map.of(
                "type", "text",
                "text", "The attached document is pages " + startPage + "-" + endPage
                        + " of the book. " + userMessage);
        Map<String, Object> finalUserMessage = Map.of(
                "role", "user",
                "content", List.of(documentBlock, textBlock));

        List<Object> messages = new ArrayList<>(historyMessages);
        messages.add(finalUserMessage);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("max_tokens", MAX_TOKENS);
        body.put("system", systemPrompt);
        body.put("messages", messages);
        return body;
    }

    private String replyText(JsonNode root) {
        for (JsonNode block : root.path("content")) {
            if ("text".equals(block.path("type").asString())) {
                String text = block.path("text").asString();
                if (text != null && !text.isBlank()) {
                    return CUT_OFF_STOP_REASONS.contains(root.path("stop_reason").asString())
                            ? text + CUT_OFF_NOTICE
                            : text;
                }
            }
        }
        throw new AiProviderException("claude", "response contained no text block", null);
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
                    .timeout(OutboundHttp.CALL_TIMEOUT)
                    .block();
            return replyText(JSON.readTree(response));
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
     * {@code text_delta}. Events are consumed on the calling thread, so a slow
     * reader never blocks the HTTP client's event loop — the domain layer stays
     * synchronous; only the wire is incremental.
     */
    @Override
    public void converseStream(String systemPrompt, List<Message> context, String userMessage, TokenStream stream) {
        streamMessages(requestBody(systemPrompt, context, userMessage, true), stream);
    }

    @Override
    public void converseLongStream(String systemPrompt, List<Message> context, String userMessage,
                                   ActivityContent content, TokenStream stream) {
        Map<String, Object> body = switch (content) {
            case ActivityContent.DocumentReference doc -> documentRequestBody(
                    systemPrompt, context, userMessage, doc.pdfBytes(), doc.startPage(), doc.endPage());
            case ActivityContent.PlainText ignored -> requestBody(systemPrompt, context, userMessage, false);
        };
        body.put("max_tokens", LONG_STREAM_MAX_TOKENS);
        body.put("stream", true);
        streamMessages(body, stream);
    }

    private void streamMessages(Map<String, Object> body, TokenStream stream) {
        StringBuilder full = new StringBuilder();
        String stopReason = null;

        try {
            Iterable<ServerSentEvent<String>> events = webClient.post()
                    .uri("/messages")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToFlux(new ParameterizedTypeReference<ServerSentEvent<String>>() { })
                    .timeout(OutboundHttp.CALL_TIMEOUT)
                    .toIterable();
            for (ServerSentEvent<String> event : events) {
                String eventStopReason = handleStreamEvent(event.data(), full, stream);
                stopReason = eventStopReason != null ? eventStopReason : stopReason;
            }

            if (full.toString().isBlank()) {
                stream.onError(new AiProviderException("claude", "stream produced no text (stop_reason=" + stopReason + ")", null));
                return;
            }
            // No stop reason at all means the connection ended before the model did.
            if (stopReason == null || CUT_OFF_STOP_REASONS.contains(stopReason)) {
                log.warn("Claude stream cut off stopReason={} chars={}", stopReason, full.length());
                full.append(CUT_OFF_NOTICE);
                stream.onDelta(CUT_OFF_NOTICE);
            }
            stream.onComplete(full.toString());
        } catch (AiProviderException e) {
            stream.onError(e);
        } catch (Exception e) {
            log.error("Claude streaming request failed", e);
            stream.onError(new AiProviderException("claude", e));
        }
    }

    /** @return the stop reason if this event carried one, else null */
    private String handleStreamEvent(String data, StringBuilder full, TokenStream stream) {
        if (data == null || data.isBlank()) {
            return null;
        }
        JsonNode node = JSON.readTree(data);
        String type = node.path("type").asString();
        if ("content_block_delta".equals(type) && "text_delta".equals(node.path("delta").path("type").asString())) {
            String text = node.path("delta").path("text").asString();
            if (text != null && !text.isEmpty()) {
                full.append(text);
                stream.onDelta(text);
            }
        } else if ("message_delta".equals(type) && node.path("delta").path("stop_reason").isString()) {
            return node.path("delta").path("stop_reason").asString();
        } else if ("error".equals(type)) {
            throw new AiProviderException("claude", node.path("error").path("message").asString(), null);
        }
        return null;
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
