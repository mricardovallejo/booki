package com.booki.voice;

/**
 * The audio for a voice turn could not be transcribed, so there is no user
 * input to run. Surfaced to the client as a controlled error (the reader can
 * retry or type). A failed <em>reply</em> synthesis (TTS) does not raise this —
 * that path degrades to text-only.
 */
public class VoiceTranscriptionException extends RuntimeException {

    public VoiceTranscriptionException(String message, Throwable cause) {
        super(message, cause);
    }
}
