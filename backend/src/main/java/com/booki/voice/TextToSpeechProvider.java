package com.booki.voice;

/**
 * Server-side text-to-speech for BooKI's voice replies (see ADR-009).
 * Credentials stay on the backend.
 *
 * <p>Best-effort by contract: callers persist and return the text reply
 * regardless, and fall back to text-only (or browser {@code speechSynthesis})
 * when this is unavailable. Synchronous for now; a streaming variant is a later
 * addition as a separate method.
 */
public interface TextToSpeechProvider {

    /** Whether this provider has enough configuration (e.g. an API key) to be used. */
    boolean isConfigured();

    /**
     * @param text     BooKI's reply text
     * @param language the session language ({@code en} / {@code es} / {@code fr}) — a provider may use it to pick a voice
     */
    Speech synthesize(String text, String language);

    record Speech(byte[] audio, String contentType) {
    }
}
