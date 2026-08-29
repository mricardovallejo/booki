package com.booki.voice;

import com.booki.conversation.ConversationEngine;
import com.booki.conversation.ConversationRequest;
import com.booki.conversation.ConversationResult;
import com.booki.domain.Message;
import com.booki.domain.Session;
import com.booki.repository.SessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VoiceConversationServiceTest {

    private static final long USER_ID = 7L;
    private static final long SESSION_ID = 1L;

    @Mock private SessionRepository sessionRepository;
    @Mock private SpeechToTextProvider speechToText;
    @Mock private TextToSpeechProvider textToSpeech;
    @Mock private ConversationEngine conversationEngine;
    @Mock private Session session;

    private VoiceConversationService service;

    @BeforeEach
    void setUp() {
        service = new VoiceConversationService(sessionRepository, speechToText, textToSpeech, conversationEngine);
        ReflectionTestUtils.setField(service, "maxAudioBytes", 10_485_760L);

        when(sessionRepository.findByIdAndUserId(SESSION_ID, USER_ID)).thenReturn(Optional.of(session));
        lenient().when(session.getLanguage()).thenReturn("es");
        lenient().when(conversationEngine.converse(any())).thenReturn(
                new ConversationResult(message(Message.Speaker.USER, "hola"), message(Message.Speaker.BOOKI, "reply")));
    }

    @Test
    void transcribesThenRunsThroughEngineAsAVoiceTurn() {
        when(speechToText.transcribe(any(), anyString(), any())).thenReturn(new SpeechToTextProvider.Transcript("hola BooKI"));
        when(textToSpeech.isConfigured()).thenReturn(true);
        when(textToSpeech.synthesize(anyString(), any()))
                .thenReturn(new TextToSpeechProvider.Speech(new byte[]{1, 2, 3}, "audio/mpeg"));

        VoiceConversationService.VoiceTurnResult result =
                service.processTurn(USER_ID, SESSION_ID, new byte[]{9, 9}, "audio/webm", null, true);

        ArgumentCaptor<ConversationRequest> captor = ArgumentCaptor.forClass(ConversationRequest.class);
        verify(conversationEngine).converse(captor.capture());
        assertThat(captor.getValue().text()).isEqualTo("hola BooKI");
        assertThat(captor.getValue().inputType()).isEqualTo(Message.InputType.VOICE);

        assertThat(result.replyAudio()).containsExactly(1, 2, 3);
        assertThat(result.replyAudioContentType()).isEqualTo("audio/mpeg");
        assertThat(result.botMessage().getMessage()).isEqualTo("reply");
    }

    @Test
    void transcriptionFailureRaisesControlledErrorAndSkipsTheEngine() {
        when(speechToText.transcribe(any(), any(), any()))
                .thenThrow(new VoiceProviderException("stt", new RuntimeException("boom")));

        assertThatThrownBy(() -> service.processTurn(USER_ID, SESSION_ID, new byte[]{9}, "audio/webm", null, true))
                .isInstanceOf(VoiceTranscriptionException.class);

        verify(conversationEngine, never()).converse(any());
    }

    @Test
    void ttsNotConfiguredReturnsTextOnly() {
        when(speechToText.transcribe(any(), any(), any())).thenReturn(new SpeechToTextProvider.Transcript("hola"));
        when(textToSpeech.isConfigured()).thenReturn(false);

        VoiceConversationService.VoiceTurnResult result =
                service.processTurn(USER_ID, SESSION_ID, new byte[]{9}, "audio/webm", null, true);

        assertThat(result.replyAudio()).isNull();
        assertThat(result.botMessage().getMessage()).isEqualTo("reply");
        verify(textToSpeech, never()).synthesize(anyString(), any());
    }

    @Test
    void wantsAudioReplyFalseSkipsTtsEntirely() {
        when(speechToText.transcribe(any(), any(), any())).thenReturn(new SpeechToTextProvider.Transcript("hola"));

        VoiceConversationService.VoiceTurnResult result =
                service.processTurn(USER_ID, SESSION_ID, new byte[]{9}, "audio/webm", null, false);

        assertThat(result.replyAudio()).isNull();
        assertThat(result.botMessage().getMessage()).isEqualTo("reply");
        verify(textToSpeech, never()).isConfigured();
        verify(textToSpeech, never()).synthesize(anyString(), any());
    }

    @Test
    void ttsFailureDegradesToTextOnly() {
        when(speechToText.transcribe(any(), any(), any())).thenReturn(new SpeechToTextProvider.Transcript("hola"));
        when(textToSpeech.isConfigured()).thenReturn(true);
        when(textToSpeech.synthesize(anyString(), any()))
                .thenThrow(new VoiceProviderException("tts", new RuntimeException("nope")));

        VoiceConversationService.VoiceTurnResult result =
                service.processTurn(USER_ID, SESSION_ID, new byte[]{9}, "audio/webm", null, true);

        assertThat(result.replyAudio()).isNull();
        assertThat(result.userMessage().getMessage()).isEqualTo("hola");
    }

    @Test
    void rejectsOversizedAudio() {
        ReflectionTestUtils.setField(service, "maxAudioBytes", 1L);

        assertThatThrownBy(() -> service.processTurn(USER_ID, SESSION_ID, new byte[]{1, 2, 3}, "audio/webm", null, true))
                .isInstanceOf(IllegalArgumentException.class);
        verify(speechToText, never()).transcribe(any(), any(), any());
    }

    private static Message message(Message.Speaker speaker, String text) {
        Message m = new Message();
        m.setSpeaker(speaker);
        m.setInputType(speaker == Message.Speaker.USER ? Message.InputType.VOICE : Message.InputType.TEXT);
        m.setMessage(text);
        return m;
    }
}
