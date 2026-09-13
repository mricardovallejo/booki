package com.booki.ai;

import com.booki.config.OutboundHttp;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
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
     * {@code POST /v1/files}, multipart with {@code purpose=user_data} — the
     * purpose the Responses API expects for a file referenced as {@code
     * input_file}. See platform.openai.com "PDF files" guide.
     */
    @Override
    public String uploadDocument(byte[] pdfBytes, String title) {
        MultipartBodyBuilder parts = new MultipartBodyBuilder();
        parts.part("purpose", "user_data");
        parts.part("file", new ByteArrayResource(pdfBytes) {
            @Override
            public String getFilename() {
                return title;
            }
        }).contentType(MediaType.APPLICATION_PDF);

        try {
            String response = webClient.post()
                    .uri("/files")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(BodyInserters.fromMultipartData(parts.build()))
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(OutboundHttp.CALL_TIMEOUT)
                    .block();
            String fileId = JSON.readTree(response).path("id").asString();
            if (fileId == null || fileId.isBlank()) {
                throw new AiProviderException("openai", "file upload response had no id", null);
            }
            return fileId;
        } catch (AiProviderException e) {
            throw e;
        } catch (Exception e) {
            log.error("OpenAI file upload failed", e);
            throw new AiProviderException("openai", e);
        }
    }

    /**
     * {@code POST /v1/responses} — a different endpoint and body/response shape
     * than Chat Completions. The final user turn attaches the file by id
     * ({@code type: input_file}) plus the text instruction; history replays as
     * plain {@code input_text} turns. Reply text lives at
     * {@code output[].content[].text} (type {@code output_text}), not
     * {@code choices[0].message.content}.
     */
    @Override
    public String converseWithDocument(String systemPrompt, List<Message> context, String userMessage,
                                        String fileId, int startPage, int endPage) {
        List<Object> input = new ArrayList<>();
        input.add(Map.of("role", "developer", "content", List.of(
                Map.of("type", "input_text", "text", systemPrompt))));
        for (Message m : context) {
            input.add(Map.of("role", m.role(), "content", List.of(
                    Map.of("type", "input_text", "text", m.content()))));
        }
        String instruction = "Use only pages " + startPage + " to " + endPage
                + " of the attached document for this. " + userMessage;
        input.add(Map.of("role", "user", "content", List.of(
                Map.of("type", "input_file", "file_id", fileId),
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
        } catch (Exception e) {
            log.error("OpenAI document request failed", e);
            throw new AiProviderException("openai", e);
        }
    }
}
