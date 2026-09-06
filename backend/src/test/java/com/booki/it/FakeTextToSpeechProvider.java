package com.booki.it;

import com.booki.voice.TextToSpeechProvider;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Hand-written TTS double: "synthesizes" any reply into fixed MP3 bytes so the
 * voice round trip (base64 audio in the response) is fully exercised.
 */
public class FakeTextToSpeechProvider implements TextToSpeechProvider {

    private static final byte[] FAKE_MP3 = "FAKE-MP3-BYTES".getBytes();

    private final AtomicReference<String> lastText = new AtomicReference<>();
    private final AtomicReference<String> lastLanguage = new AtomicReference<>();

    public String lastText() {
        return lastText.get();
    }

    public String lastLanguage() {
        return lastLanguage.get();
    }

    /** Drop state carried over from an earlier test (one Spring context per package). */
    public void clear() {
        lastText.set(null);
        lastLanguage.set(null);
    }

    @Override
    public boolean isConfigured() {
        return true;
    }

    @Override
    public Speech synthesize(String text, String language) {
        lastText.set(text);
        lastLanguage.set(language);
        return new Speech(FAKE_MP3, "audio/mpeg");
    }
}
