package com.booki.ai;

import com.booki.config.OutboundHttp;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Plain conversation stays on Chat Completions (inherited from
 * {@link OpenAiCompatibleProvider}, shared with Kimi). Document turns use the
 * newer Responses API instead — Chat Completions has no way to attach a file —
 * so this class talks to two different OpenAI endpoints depending on the call.
 */
@Slf4j
@Component("openai")
public class OpenAiProvider extends OpenAiCompatibleProvider {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final int MAX_OUTPUT_TOKENS = 4096;

    public OpenAiProvider(@Value("${booki.ai.openai.api-key}") String apiKey,
                          @Value("${booki.ai.openai.model}") String model) {
        super("https://api.openai.com/v1", apiKey, model);
    }

    @Override
    public boolean supportsDocuments() {
        return true;
    }

    /**
     * {@code POST /v1/responses} — a different endpoint and body/response shape
     * than Chat Completions. The final user turn attaches the PDF inline as
     * base64 ({@code type: input_file}, {@code file_data} as a data URI — no
     * Files API, no upload step; {@code pdfBytes} is only ever the small slice
     * of pages this turn needs, physically cut by {@code ActivityContentService},
     * never the whole book) plus the text instruction. Reply text lives at
     * {@code output[].content[].text} (type {@code output_text}), not
     * {@code choices[0].message.content}.
     */
    @Override
    public String converseWithDocument(String systemPrompt, List<Message> context, String userMessage,
                                        byte[] pdfBytes, int startPage, int endPage) {
        List<Object> input = new ArrayList<>();
        input.add(Map.of("role", "developer", "content", List.of(
                Map.of("type", "input_text", "text", systemPrompt))));
        for (Message m : context) {
            // A replayed assistant turn must use "output_text", not "input_text" —
            // the Responses API 400s on that combination ("Invalid value: 'input_text'.
            // Supported values are: 'output_text' and 'refusal'"), only once a
            // session has at least one prior BooKI reply in its history.
            String partType = "assistant".equals(m.role()) ? "output_text" : "input_text";
            input.add(Map.of("role", m.role(), "content", List.of(
                    Map.of("type", partType, "text", m.content()))));
        }
        String instruction = "The attached document is pages " + startPage + "-" + endPage
                + " of the book. " + userMessage;
        String fileData = "data:application/pdf;base64," + Base64.getEncoder().encodeToString(pdfBytes);
        input.add(Map.of("role", "user", "content", List.of(
                Map.of("type", "input_file", "filename", "pages-" + startPage + "-" + endPage + ".pdf",
                        "file_data", fileData),
                Map.of("type", "input_text", "text", instruction))));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model());
        body.put("input", input);
        body.put("max_output_tokens", MAX_OUTPUT_TOKENS);

        try {
            String response = webClient.post()
                    .uri("/responses")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(OutboundHttp.CALL_TIMEOUT)
                    .block();
            JsonNode root = JSON.readTree(response);
            for (JsonNode output : root.path("output")) {
                for (JsonNode block : output.path("content")) {
                    if ("output_text".equals(block.path("type").asString())) {
                        String text = block.path("text").asString();
                        if (text != null && !text.isBlank()) {
                            return text;
                        }
                    }
                }
            }
            throw new AiProviderException("openai", "response contained no output text", null);
        } catch (AiProviderException e) {
            throw e;
        } catch (WebClientResponseException e) {
            // The status/URL alone (e.getMessage()) hides *why* OpenAI rejected the
            // request — the actual reason is in the response body, only reachable
            // via getResponseBodyAsString().
            log.error("OpenAI document request failed: {}", e.getResponseBodyAsString());
            throw new AiProviderException("openai", e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            log.error("OpenAI document request failed", e);
            throw new AiProviderException("openai", e);
        }
    }
}
