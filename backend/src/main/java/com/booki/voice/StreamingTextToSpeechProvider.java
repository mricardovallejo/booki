package com.booki.voice;

/**
 * Optional companion to {@link TextToSpeechProvider}: a provider that can emit
 * audio incrementally implements this in addition. {@code synthesize()} is
 * never changed.
 *
 * <p>Shape only for now — no implementation. Streaming TTS only pays off once
 * the transport can forward chunks as they arrive; today a voice reply is
 * base64 in a single JSON response ({@code VoiceTurnResponse}), which can't
 * surface partial audio. See ADR-010: this lands together with an SSE/WebSocket
 * voice channel when there is a concrete low-latency requirement.
 *
 * <p>Contract (when implemented): never throws; terminates {@link AudioStream}
 * exactly once via {@code onComplete} or {@code onError}.
 */
public interface StreamingTextToSpeechProvider {

    void synthesizeStream(String text, String language, AudioStream stream);

    interface AudioStream {
        /** A chunk of encoded audio, in order. */
        void onChunk(byte[] audio);

        /** Terminal success. {@code contentType} describes the concatenated stream (e.g. {@code audio/mpeg}). */
        void onComplete(String contentType);

        /** Terminal failure (typically a {@link VoiceProviderException}). */
        void onError(RuntimeException error);
    }
}
