package com.booki.voice;

/**
 * Optional companion to {@link SpeechToTextProvider}: incremental transcription
 * of audio that arrives over time. {@code transcribe()} is never changed.
 *
 * <p>Shape only for now — no implementation. Unlike streaming AI/TTS (which only
 * need a streaming response), incremental STT needs a streaming <em>request</em>:
 * audio pushed as it is captured. That requires a bidirectional transport
 * (WebSocket / WebRTC), which BooKI does not add until there is a concrete
 * low-latency voice requirement (ADR-010). The current voice path uploads a
 * finished clip to {@code POST /sessions/{id}/voice}.
 *
 * <p>Contract (when implemented): {@code open} returns a session the caller
 * feeds with {@link Session#push}; the sink is terminated once via
 * {@code onFinal} (or {@code onError}) after {@link Session#close}.
 */
public interface StreamingSpeechToTextProvider {

    Session open(String language, TranscriptSink sink);

    interface Session {
        /** Feed a chunk of captured audio. */
        void push(byte[] audioChunk);

        /** No more audio — flush and finalise. */
        void close();
    }

    interface TranscriptSink {
        /** A revisable partial transcript. */
        void onPartial(String text);

        /** The settled transcript for the utterance. */
        void onFinal(String text);

        /** Terminal failure (typically a {@link VoiceProviderException}). */
        void onError(RuntimeException error);
    }
}
