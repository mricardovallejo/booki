package com.booki.voice;

/**
 * An STT or TTS provider call failed — network error, upstream 4xx/5xx, missing
 * credentials, or an empty/unusable payload. Mirrors
 * {@link com.booki.ai.AiProviderException}: providers throw this rather than
 * returning a plausible-looking empty result.
 */
public class VoiceProviderException extends RuntimeException {

    private final String stage;

    public VoiceProviderException(String stage, String message, Throwable cause) {
        super("[" + stage + "] " + message, cause);
        this.stage = stage;
    }

    public VoiceProviderException(String stage, Throwable cause) {
        this(stage, "request failed", cause);
    }

    /** {@code "stt"} or {@code "tts"}. */
    public String getStage() {
        return stage;
    }
}
