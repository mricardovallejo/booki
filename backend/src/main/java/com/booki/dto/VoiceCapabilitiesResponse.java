package com.booki.dto;

/** What the deployment can do with voice, so the client picks the cloud path or the browser fallback up front. */
public record VoiceCapabilitiesResponse(boolean stt, boolean tts) {
}
