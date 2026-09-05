package com.booki.it;

import com.booki.dto.VoiceCapabilitiesResponse;
import com.booki.dto.VoiceTurnResponse;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Voice turns over HTTP with the hand-written STT/TTS doubles: the real
 * {@code VoiceConversationService} (size caps, transcription failure policy,
 * best-effort TTS) plus {@code VoiceController} multipart handling.
 */
class VoiceIT extends IntegrationTestBase {

    private static final int TEN_MB = 10 * 1024 * 1024;

    @Test
    void capabilitiesReportFakeProviders() {
        ResponseEntity<VoiceCapabilitiesResponse> response = rest.getForEntity("/api/voice/capabilities", VoiceCapabilitiesResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().stt()).isTrue();
        assertThat(response.getBody().tts()).isTrue();
    }

    @Test
    void voiceTurnTranscribesConversesAndSpeaks() {
        AuthData user = register(uniqueEmail("voice"));
        Long sessionId = createSession(user, uploadThreePageDocument(user), 1, 3);

        ResponseEntity<VoiceTurnResponse> response = postVoice(user, sessionId, "fake-audio".getBytes(), "audio.webm", null, true);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        VoiceTurnResponse body = response.getBody();
        assertThat(body.userMessage().getInputType()).isEqualTo("VOICE");
        assertThat(body.userMessage().getMessage()).isEqualTo("fake transcript");
        assertThat(body.botMessage().getMessage()).isEqualTo("Fake AI reply");
        assertThat(body.audioBase64()).isNotBlank();
        assertThat(body.audioContentType()).isEqualTo("audio/mpeg");

        assertThat(fakeStt.lastLanguage()).isEqualTo("en");
        assertThat(fakeTts.lastText()).isEqualTo("Fake AI reply");
        assertThat(fakeTts.lastLanguage()).isEqualTo("en");
    }

    @Test
    void voiceTurnSkipsSpeechWhenReaderDeclinesAudio() {
        AuthData user = register(uniqueEmail("voice-off"));
        Long sessionId = createSession(user, uploadThreePageDocument(user), 1, 3);

        ResponseEntity<VoiceTurnResponse> response = postVoice(user, sessionId, "fake-audio".getBytes(), "audio.webm", null, false);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().audioBase64()).isNull();
        assertThat(fakeTts.lastText()).isNull();
    }

    @Test
    void voiceTurnSupportsCapabilityHints() {
        AuthData user = register(uniqueEmail("voice-hint"));
        Long sessionId = createSession(user, uploadThreePageDocument(user), 1, 3);

        ResponseEntity<VoiceTurnResponse> response = postVoice(user, sessionId, "fake-audio".getBytes(), "audio.webm", "summary", true);

        assertThat(response.getBody().botMessage().getMessage()).isEqualTo("Summary of the pages.");
    }

    @Test
    void voiceRejectsEmptyAndOversizedUploads() {
        AuthData user = register(uniqueEmail("voice-size"));
        Long sessionId = createSession(user, uploadThreePageDocument(user), 1, 3);

        ResponseEntity<Map<String, String>> empty = postVoice(user, sessionId, new byte[0], "empty.webm", null, true, errorType());
        assertThat(empty.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(empty.getBody()).containsEntry("error", "The audio upload was empty");

        ResponseEntity<Map<String, String>> oversized = postVoice(user, sessionId, new byte[TEN_MB + 1], "big.webm", null, true, errorType());
        assertThat(oversized.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(oversized.getBody()).containsEntry("error", "The audio upload is too large");
    }

    @Test
    void voiceTurnChecksOwnership() {
        AuthData user = register(uniqueEmail("voice-own"));
        AuthData other = register(uniqueEmail("voice-own-other"));
        Long sessionId = createSession(user, uploadThreePageDocument(user), 1, 3);

        ResponseEntity<Map<String, String>> response = postVoice(other, sessionId, "fake-audio".getBytes(), "audio.webm", null, true, errorType());
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    private ResponseEntity<VoiceTurnResponse> postVoice(AuthData user, Long sessionId, byte[] audio, String filename,
                                                        String capabilityHint, boolean wantsAudioReply) {
        return rest.exchange(voiceUrl(sessionId, capabilityHint, wantsAudioReply), HttpMethod.POST,
                new HttpEntity<>(audioMultipart(audio, filename), auth(user.token())), VoiceTurnResponse.class);
    }

    private ResponseEntity<Map<String, String>> postVoice(AuthData user, Long sessionId, byte[] audio, String filename,
                                                          String capabilityHint, boolean wantsAudioReply,
                                                          ParameterizedTypeReference<Map<String, String>> type) {
        return rest.exchange(voiceUrl(sessionId, capabilityHint, wantsAudioReply), HttpMethod.POST,
                new HttpEntity<>(audioMultipart(audio, filename), auth(user.token())), type);
    }

    private String voiceUrl(Long sessionId, String capabilityHint, boolean wantsAudioReply) {
        StringBuilder url = new StringBuilder("/api/sessions/").append(sessionId).append("/voice?wantsAudioReply=").append(wantsAudioReply);
        if (capabilityHint != null) {
            url.append("&capabilityHint=").append(capabilityHint);
        }
        return url.toString();
    }

    private MultiValueMap<String, Object> audioMultipart(byte[] audio, String filename) {
        return multipart("audio", audio, filename, MediaType.parseMediaType("audio/webm"));
    }

    private ParameterizedTypeReference<Map<String, String>> errorType() {
        return new ParameterizedTypeReference<>() {
        };
    }
}
