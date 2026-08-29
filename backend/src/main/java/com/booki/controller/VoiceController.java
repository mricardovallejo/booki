package com.booki.controller;

import com.booki.dto.MessageResponse;
import com.booki.dto.VoiceCapabilitiesResponse;
import com.booki.dto.VoiceTurnResponse;
import com.booki.util.SecurityUtil;
import com.booki.voice.SpeechToTextProvider;
import com.booki.voice.TextToSpeechProvider;
import com.booki.voice.VoiceConversationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Base64;

/**
 * The voice transport for the conversation. {@code POST /sessions/{id}/voice}
 * is the audio-in/audio-out counterpart of {@code POST /sessions/{id}/messages}
 * — both go through the same ConversationEngine. Credentials for STT/TTS stay
 * server-side; the browser only captures and plays audio.
 */
@RestController
@RequiredArgsConstructor
public class VoiceController {

    private final VoiceConversationService voiceConversationService;
    private final SpeechToTextProvider speechToText;
    private final TextToSpeechProvider textToSpeech;

    @GetMapping("/api/voice/capabilities")
    public VoiceCapabilitiesResponse capabilities() {
        return new VoiceCapabilitiesResponse(speechToText.isConfigured(), textToSpeech.isConfigured());
    }

    @PostMapping(path = "/api/sessions/{id}/voice", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<VoiceTurnResponse> voiceTurn(
            @PathVariable Long id,
            @RequestParam("audio") MultipartFile audio,
            @RequestParam(value = "capabilityHint", required = false) String capabilityHint) {

        byte[] bytes;
        try {
            bytes = audio.getBytes();
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not read the audio upload");
        }

        VoiceConversationService.VoiceTurnResult result = voiceConversationService.processTurn(
                SecurityUtil.currentUserId(), id, bytes, audio.getContentType(), capabilityHint);

        VoiceTurnResponse body = new VoiceTurnResponse(
                MessageResponse.of(result.userMessage()),
                MessageResponse.of(result.botMessage()),
                result.replyAudio() == null ? null : Base64.getEncoder().encodeToString(result.replyAudio()),
                result.replyAudioContentType());
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }
}
