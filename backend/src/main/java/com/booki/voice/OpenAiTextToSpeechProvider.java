package com.booki.voice;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

/**
 * Text-to-speech via OpenAI's {@code /audio/speech}. Returns MP3 bytes. Same
 * story as {@link OpenAiSpeechToTextProvider}: OpenAI wire format, provider-
 * neutral interface, inactive without an API key.
 */
@Slf4j
@Component
public class OpenAiTextToSpeechProvider implements TextToSpeechProvider {

    private final WebClient webClient;
    private final String model;
    private final String voice;
    private final int maxInputChars;
    private final boolean configured;

    public OpenAiTextToSpeechProvider(
            @Value("${booki.voice.openai.api-key:}") String apiKey,
            @Value("${booki.voice.openai.base-url:https://api.openai.com/v1}") String baseUrl,
            @Value("${booki.voice.openai.tts-model:gpt-4o-mini-tts}") String model,
            @Value("${booki.voice.openai.tts-voice:alloy}") String voice,
            @Value("${booki.voice.openai.tts-max-input-chars:1200}") int maxInputChars) {
        this.configured = apiKey != null && !apiKey.isBlank();
        this.model = model;
        this.voice = voice;
        this.maxInputChars = Math.max(200, maxInputChars);
        // Spring's default in-memory buffer for WebClient responses is 256 KB; a
        // TTS mp3 clip routinely exceeds that, which fails the whole response
        // with a DataBufferLimitException even though OpenAI returned 200 OK.
        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + (apiKey == null ? "" : apiKey))
                .exchangeStrategies(ExchangeStrategies.builder()
                        .codecs(codecs -> codecs.defaultCodecs().maxInMemorySize(5 * 1024 * 1024))
                        .build())
                .build();
    }

    @Override
    public boolean isConfigured() {
        return configured;
    }

    @Override
    public Speech synthesize(String text, String language) {
        if (!configured) {
            throw new VoiceProviderException("tts", "no OpenAI API key configured", null);
        }
        String input = (text != null && text.length() > maxInputChars)
                ? text.substring(0, maxInputChars)
                : (text == null ? "" : text);
        if (input.isBlank()) {
            throw new VoiceProviderException("tts", "nothing to synthesize", null);
        }

        long startedAt = System.currentTimeMillis();
        try {
            byte[] audio = webClient.post()
                    .uri("/audio/speech")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(Map.of(
                            "model", model,
                            "voice", voice,
                            "input", input,
                            "response_format", "mp3"))
                    .retrieve()
                    .bodyToMono(byte[].class)
                    .block();
            if (audio == null || audio.length == 0) {
                throw new VoiceProviderException("tts", "audio response was empty", null);
            }
            log.info("TTS call completed model={} voice={} durationMs={}",
                    model, voice, System.currentTimeMillis() - startedAt);
            return new Speech(audio, "audio/mpeg");
        } catch (VoiceProviderException e) {
            throw e;
        } catch (Exception e) {
            log.error("TTS call failed model={} voice={} durationMs={}",
                    model, voice, System.currentTimeMillis() - startedAt, e);
            throw new VoiceProviderException("tts", e);
        }
    }
}
