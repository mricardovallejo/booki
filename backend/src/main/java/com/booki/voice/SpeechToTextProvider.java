package com.booki.voice;

/**
 * Server-side speech-to-text. Replaces the browser {@code SpeechRecognition}
 * dependency for BooKI's core voice path (see ADR-009); credentials stay on the
 * backend.
 *
 * <p>Deliberately minimal and synchronous for now. A streaming variant
 * (incremental transcription) is a later addition as a separate method, so this
 * contract does not need to change when Phase 5 arrives.
 */
public interface SpeechToTextProvider {

    /** Whether this provider has enough configuration (e.g. an API key) to be used. */
    boolean isConfigured();

    /**
     * @param audio       raw audio bytes as uploaded by the browser (typically webm/opus from MediaRecorder)
     * @param contentType the browser-reported MIME type, or {@code null}
     * @param language    the session language ({@code en} / {@code es} / {@code fr}) — drives recognition locale
     */
    Transcript transcribe(byte[] audio, String contentType, String language);

    record Transcript(String text) {
    }
}
