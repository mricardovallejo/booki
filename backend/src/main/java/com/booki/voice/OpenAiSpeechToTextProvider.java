package com.booki.voice;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Speech-to-text via OpenAI's {@code /audio/transcriptions} (Whisper). The wire
 * format is OpenAI-specific but the {@link SpeechToTextProvider} interface is
 * not — a Google/Azure/Deepgram implementation slots in the same way.
 *
 * <p>Inactive (returns {@code isConfigured() == false}, throws on use) when no
 * API key is set, so a deployment without a voice key simply falls back to the
 * browser recognizer.
 */
@Slf4j
@Component
public class OpenAiSpeechToTextProvider implements SpeechToTextProvider {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final WebClient webClient;
    private final String model;
    private final boolean configured;

    public OpenAiSpeechToTextProvider(
            @Value("${booki.voice.openai.api-key:}") String apiKey,
            @Value("${booki.voice.openai.base-url:https://api.openai.com/v1}") String baseUrl,
            @Value("${booki.voice.openai.stt-model:whisper-1}") String model) {
        this.configured = apiKey != null && !apiKey.isBlank();
        this.model = model;
        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + (apiKey == null ? "" : apiKey))
                .build();
    }

    @Override
    public boolean isConfigured() {
        return configured;
    }

    @Override
    public Transcript transcribe(byte[] audio, String contentType, String language) {
        if (!configured) {
            throw new VoiceProviderException("stt", "no OpenAI API key configured", null);
        }
        String mime = (contentType == null || contentType.isBlank()) ? "audio/webm" : contentType;

        MultipartBodyBuilder form = new MultipartBodyBuilder();
        form.part("file", new NamedByteArrayResource(audio, "audio" + extensionFor(mime)))
                .contentType(MediaType.parseMediaType(mime));
        form.part("model", model);
        form.part("response_format", "json");
        if (language != null && !language.isBlank()) {
            form.part("language", language);
        }

        try {
            String response = webClient.post()
                    .uri("/audio/transcriptions")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(BodyInserters.fromMultipartData(form.build()))
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            JsonNode root = JSON.readTree(response);
            String text = root.path("text").asString();
            if (text == null || text.isBlank()) {
                throw new VoiceProviderException("stt", "transcript was empty", null);
            }
            return new Transcript(text.strip());
        } catch (VoiceProviderException e) {
            throw e;
        } catch (Exception e) {
            log.error("OpenAI transcription failed", e);
            throw new VoiceProviderException("stt", e);
        }
    }

    private static String extensionFor(String mime) {
        String base = mime.toLowerCase();
        if (base.contains("webm")) return ".webm";
        if (base.contains("ogg")) return ".ogg";
        if (base.contains("mp4") || base.contains("m4a")) return ".m4a";
        if (base.contains("mpeg") || base.contains("mp3")) return ".mp3";
        if (base.contains("wav")) return ".wav";
        return ".webm";
    }

    /** ByteArrayResource with a filename — the multipart {@code file} part needs one for the API to accept it. */
    private static final class NamedByteArrayResource extends ByteArrayResource {
        private final String filename;

        NamedByteArrayResource(byte[] bytes, String filename) {
            super(bytes);
            this.filename = filename;
        }

        @Override
        public String getFilename() {
            return filename;
        }
    }
}
