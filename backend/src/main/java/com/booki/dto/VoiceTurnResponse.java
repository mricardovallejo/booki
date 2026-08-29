package com.booki.dto;

/**
 * Result of one voice turn. {@code userMessage} and {@code botMessage} are the
 * same persisted {@link MessageResponse}s a typed turn returns; {@code audioBase64}
 * is BooKI's spoken reply (MP3) or {@code null} when TTS is unavailable — the
 * client then shows text only or uses browser {@code speechSynthesis}.
 */
public record VoiceTurnResponse(
        MessageResponse userMessage,
        MessageResponse botMessage,
        String audioBase64,
        String audioContentType) {
}
