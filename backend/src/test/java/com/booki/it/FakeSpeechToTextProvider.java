package com.booki.it;

import com.booki.voice.SpeechToTextProvider;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Hand-written STP double: every upload "transcribes" to a fixed text, so a
 * voice turn exercises the real {@code VoiceConversationService} pipeline
 * end to end without any provider credentials or network.
 */
public class FakeSpeechToTextProvider implements SpeechToTextProvider {

    private final AtomicReference<String> transcript = new AtomicReference<>("fake transcript");
    private final AtomicReference<String> lastLanguage = new AtomicReference<>();

    public void setTranscript(String text) {
        transcript.set(text);
    }

    public String lastLanguage() {
        return lastLanguage.get();
    }

    /** Drop state carried over from an earlier test (one Spring context per package). */
    public void clear() {
        transcript.set("fake transcript");
        lastLanguage.set(null);
    }

    @Override
    public boolean isConfigured() {
        return true;
    }

    @Override
    public Transcript transcribe(byte[] audio, String contentType, String language) {
        lastLanguage.set(language);
        return new Transcript(transcript.get());
    }
}
