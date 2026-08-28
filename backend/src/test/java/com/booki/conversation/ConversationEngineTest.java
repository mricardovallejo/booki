package com.booki.conversation;

import com.booki.ai.AiProvider;
import com.booki.ai.AiProviderException;
import com.booki.ai.AiProviderRegistry;
import com.booki.domain.Document;
import com.booki.domain.Message;
import com.booki.domain.Session;
import com.booki.repository.DocumentPageRepository;
import com.booki.repository.MessageRepository;
import com.booki.repository.SessionRepository;
import com.booki.service.impl.SessionContextBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConversationEngineTest {

    private static final long USER_ID = 7L;
    private static final long SESSION_ID = 1L;

    @Mock private SessionRepository sessionRepository;
    @Mock private MessageRepository messageRepository;
    @Mock private DocumentPageRepository documentPageRepository;
    @Mock private AiProviderRegistry aiProviderRegistry;
    @Mock private SessionContextBuilder sessionContextBuilder;
    @Mock private AiProvider aiProvider;
    @Mock private Session session;
    @Mock private Document document;

    @Captor private ArgumentCaptor<List<AiProvider.Message>> historyCaptor;
    @Captor private ArgumentCaptor<Message> savedMessageCaptor;

    private ConversationEngine engine;

    @BeforeEach
    void setUp() {
        engine = new ConversationEngine(sessionRepository, messageRepository, documentPageRepository,
                aiProviderRegistry, sessionContextBuilder, 20, 24000);

        when(sessionRepository.findByIdAndUserId(SESSION_ID, USER_ID)).thenReturn(Optional.of(session));
        lenientSession();
        when(messageRepository.save(any(Message.class))).thenAnswer(inv -> inv.getArgument(0));
        when(documentPageRepository.findByDocumentIdAndPageNumberBetweenOrderByPageNumberAsc(any(), any(), any()))
                .thenReturn(List.of());
        when(sessionContextBuilder.buildSystemPrompt(any(), anyString())).thenReturn("system-prompt");
        when(aiProviderRegistry.get(any())).thenReturn(aiProvider);
    }

    private void lenientSession() {
        org.mockito.Mockito.lenient().when(session.getId()).thenReturn(SESSION_ID);
        org.mockito.Mockito.lenient().when(session.getDocument()).thenReturn(document);
        org.mockito.Mockito.lenient().when(session.getStartPage()).thenReturn(1);
        org.mockito.Mockito.lenient().when(session.getEndPage()).thenReturn(3);
        org.mockito.Mockito.lenient().when(session.getAiProvider()).thenReturn("claude");
        org.mockito.Mockito.lenient().when(document.getId()).thenReturn(42L);
    }

    @Test
    void sendsMostRecentMessagesInChronologicalOrder() {
        // Repository returns newest-first (matches findBy...OrderByCreatedAtDesc).
        List<Message> newestFirst = new ArrayList<>();
        for (int i = 20; i >= 1; i--) {
            newestFirst.add(message(i % 2 == 0 ? Message.Speaker.USER : Message.Speaker.BOOKI, "msg-" + i));
        }
        when(messageRepository.findBySessionIdOrderByCreatedAtDesc(eq(SESSION_ID), any())).thenReturn(newestFirst);
        when(aiProvider.converse(anyString(), anyList(), anyString())).thenReturn("BooKI reply");

        engine.converse(new ConversationRequest(USER_ID, SESSION_ID, "current question", Message.InputType.TEXT));

        verify(aiProvider).converse(eq("system-prompt"), historyCaptor.capture(), eq("current question"));
        List<AiProvider.Message> history = historyCaptor.getValue();
        assertThat(history).hasSize(20);
        assertThat(history.get(0).content()).isEqualTo("msg-1");   // oldest of the window first
        assertThat(history.get(19).content()).isEqualTo("msg-20"); // newest last
        assertThat(history).noneMatch(m -> m.content().equals("current question")); // new turn not echoed into context
    }

    @Test
    void persistsUserAndBotMessageOnSuccess() {
        when(messageRepository.findBySessionIdOrderByCreatedAtDesc(eq(SESSION_ID), any())).thenReturn(List.of());
        when(aiProvider.converse(anyString(), anyList(), anyString())).thenReturn("the answer");

        ConversationResult result = engine.converse(
                new ConversationRequest(USER_ID, SESSION_ID, "hi", Message.InputType.VOICE));

        verify(messageRepository, times(2)).save(savedMessageCaptor.capture());
        List<Message> saved = savedMessageCaptor.getAllValues();
        assertThat(saved.get(0).getSpeaker()).isEqualTo(Message.Speaker.USER);
        assertThat(saved.get(0).getInputType()).isEqualTo(Message.InputType.VOICE);
        assertThat(saved.get(1).getSpeaker()).isEqualTo(Message.Speaker.BOOKI);
        assertThat(result.botMessage().getMessage()).isEqualTo("the answer");
    }

    @Test
    void translatesProviderFailureAndDoesNotPersistFakeAnswer() {
        when(messageRepository.findBySessionIdOrderByCreatedAtDesc(eq(SESSION_ID), any())).thenReturn(List.of());
        when(aiProvider.converse(anyString(), anyList(), anyString()))
                .thenThrow(new AiProviderException("claude", new RuntimeException("boom")));

        assertThatThrownBy(() -> engine.converse(
                new ConversationRequest(USER_ID, SESSION_ID, "hi", Message.InputType.TEXT)))
                .isInstanceOf(ConversationFailedException.class);

        // Only the user's turn was persisted — no fabricated BooKI reply.
        verify(messageRepository, times(1)).save(savedMessageCaptor.capture());
        assertThat(savedMessageCaptor.getValue().getSpeaker()).isEqualTo(Message.Speaker.USER);
    }

    private static Message message(Message.Speaker speaker, String text) {
        Message m = new Message();
        m.setSpeaker(speaker);
        m.setInputType(Message.InputType.TEXT);
        m.setMessage(text);
        return m;
    }
}
