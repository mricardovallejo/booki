package com.booki.voice;

import com.booki.conversation.ConversationEngine;
import com.booki.conversation.ConversationRequest;
import com.booki.conversation.ConversationResult;
import com.booki.domain.Message;
import com.booki.domain.Session;
import com.booki.repository.SessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.NoSuchElementException;

/**
 * The voice adapter in front of {@link ConversationEngine}: audio in, audio
 * (and the persisted messages) out.
 *
 * <pre>
 *   audio -> SpeechToTextProvider -> ConversationEngine -> TextToSpeechProvider -> audio
 * </pre>
 *
 * <p>Voice and text converge here: the transcript goes through the exact same
 * {@link ConversationEngine} call as a typed message, with {@code InputType.VOICE}.
 * The reading context, history window and conversational capabilities are all
 * the engine's — this class only bridges audio.
 *
 * <p>Raw audio is never persisted; it lives only for the duration of the request.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VoiceConversationService {

    private final SessionRepository sessionRepository;
    private final SpeechToTextProvider speechToText;
    private final TextToSpeechProvider textToSpeech;
    private final ConversationEngine conversationEngine;

    @Value("${booki.voice.max-audio-bytes:10485760}")
    private long maxAudioBytes;

    public VoiceTurnResult processTurn(Long userId, Long sessionId, byte[] audio,
                                       String contentType, String capabilityHint) {
        Session session = sessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new NoSuchElementException("Session not found"));

        if (audio == null || audio.length == 0) {
            throw new IllegalArgumentException("The audio upload was empty");
        }
        if (audio.length > maxAudioBytes) {
            throw new IllegalArgumentException("The audio upload is too large");
        }

        // Session language drives recognition locale — no hardcoded es-ES.
        String language = session.getLanguage();

        String transcript;
        try {
            transcript = speechToText.transcribe(audio, contentType, language).text();
        } catch (VoiceProviderException e) {
            throw new VoiceTranscriptionException(
                    "BooKI couldn't understand the audio. Try again, or type your message.", e);
        }

        ConversationResult result = conversationEngine.converse(new ConversationRequest(
                userId, sessionId, transcript, Message.InputType.VOICE, capabilityHint));

        // TTS is best-effort: the text reply is already persisted and returned.
        byte[] replyAudio = null;
        String replyAudioType = null;
        if (textToSpeech.isConfigured()) {
            try {
                TextToSpeechProvider.Speech speech =
                        textToSpeech.synthesize(result.botMessage().getMessage(), language);
                replyAudio = speech.audio();
                replyAudioType = speech.contentType();
            } catch (VoiceProviderException e) {
                log.warn("TTS failed; returning a text-only voice turn", e);
            }
        }

        return new VoiceTurnResult(result.userMessage(), result.botMessage(), replyAudio, replyAudioType);
    }

    public record VoiceTurnResult(
            Message userMessage,
            Message botMessage,
            byte[] replyAudio,
            String replyAudioContentType) {
    }
}
