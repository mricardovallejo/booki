package com.booki.conversation;

import com.booki.ai.ActivityContent;
import com.booki.ai.ActivityContentService;
import com.booki.ai.AiProvider;
import com.booki.ai.AiProviderException;
import com.booki.ai.AiProviderRegistry;
import com.booki.conversation.capability.CapabilityRegistry;
import com.booki.conversation.capability.ConversationCapability;
import com.booki.domain.Document;
import com.booki.domain.Message;
import com.booki.domain.Session;
import com.booki.repository.MessageRepository;
import com.booki.repository.SessionRepository;
import com.booki.domain.Capability;
import com.booki.prompt.PromptAssembler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.EnumSet;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConversationEngineTest {

    private static final long USER_ID = 7L;
    private static final long SESSION_ID = 1L;

    @Mock private SessionRepository sessionRepository;
    @Mock private MessageRepository messageRepository;
    @Mock private ActivityContentService activityContentService;
    @Mock private AiProviderRegistry aiProviderRegistry;
    @Mock private PromptAssembler promptAssembler;
    @Mock private CapabilityRegistry capabilityRegistry;
    @Mock private AiProvider aiProvider;
    @Mock private Session session;
    @Mock private Document document;

    @Captor private ArgumentCaptor<List<AiProvider.Message>> historyCaptor;
    @Captor private ArgumentCaptor<Message> savedMessageCaptor;

    private ConversationEngine engine;

    @BeforeEach
    void setUp() {
        engine = new ConversationEngine(sessionRepository, messageRepository, activityContentService,
                aiProviderRegistry, promptAssembler, capabilityRegistry, 20);

        when(sessionRepository.findByIdAndUserId(SESSION_ID, USER_ID)).thenReturn(Optional.of(session));
        lenientSession();
        when(messageRepository.save(any(Message.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(activityContentService.resolve(any(), any(), anyInt(), anyInt()))
                .thenReturn(new ActivityContent.PlainText(""));
        lenient().when(activityContentService.documentTextFor(any())).thenReturn("");
        // Bridges the mocked resolver straight to the AiProvider mock, so tests
        // can keep asserting against aiProvider.converse(...) as before.
        lenient().when(activityContentService.converse(any(), any(), anyString(), anyList(), anyString()))
                .thenAnswer(inv -> aiProvider.converse(inv.getArgument(2), inv.getArgument(3), inv.getArgument(4)));
        lenient().when(promptAssembler.forChat(any(), anyString(), anyString())).thenReturn("system-prompt");
        lenient().when(promptAssembler.enabledCapabilities(any())).thenReturn(EnumSet.allOf(Capability.class));
        lenient().when(aiProviderRegistry.get(any())).thenReturn(aiProvider);
        lenient().when(capabilityRegistry.routerInstructions(any())).thenReturn("");
        lenient().when(capabilityRegistry.parseDirective(anyString())).thenReturn(Optional.empty());
    }

    private void lenientSession() {
        lenient().when(session.getId()).thenReturn(SESSION_ID);
        lenient().when(session.getDocument()).thenReturn(document);
        lenient().when(session.getStartPage()).thenReturn(1);
        lenient().when(session.getEndPage()).thenReturn(3);
        lenient().when(session.getCurrentPage()).thenReturn(3);
        lenient().when(session.getAiProvider()).thenReturn("claude");
        lenient().when(document.getId()).thenReturn(42L);
        lenient().when(document.getPageCount()).thenReturn(100);
    }

    private static ConversationRequest request(String text) {
        return new ConversationRequest(USER_ID, SESSION_ID, text, Message.InputType.TEXT, 1, 3);
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

        engine.converse(request("current question"));

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
                new ConversationRequest(USER_ID, SESSION_ID, "hi", Message.InputType.VOICE, 1, 3));

        verify(messageRepository, times(2)).save(savedMessageCaptor.capture());
        List<Message> saved = savedMessageCaptor.getAllValues();
        assertThat(saved.get(0).getSpeaker()).isEqualTo(Message.Speaker.USER);
        assertThat(saved.get(0).getInputType()).isEqualTo(Message.InputType.VOICE);
        assertThat(saved.get(1).getSpeaker()).isEqualTo(Message.Speaker.BOOKI);
        assertThat(result.botMessage().getMessage()).isEqualTo("the answer");
    }

    @Test
    void resolvesActivityContentFromTheRequestsPageRangeNotTheReadingPosition() {
        when(messageRepository.findBySessionIdOrderByCreatedAtDesc(eq(SESSION_ID), any())).thenReturn(List.of());
        when(aiProvider.converse(anyString(), anyList(), anyString())).thenReturn("an answer");

        engine.converse(new ConversationRequest(USER_ID, SESSION_ID, "tell me about this", Message.InputType.TEXT, 10, 12));

        verify(activityContentService).resolve(document, aiProvider, 10, 12);
    }

    @Test
    void missingPageRangeIsRejected() {
        when(messageRepository.findBySessionIdOrderByCreatedAtDesc(eq(SESSION_ID), any())).thenReturn(List.of());

        assertThatThrownBy(() -> engine.converse(
                new ConversationRequest(USER_ID, SESSION_ID, "hi", Message.InputType.TEXT, null, null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void translatesProviderFailureAndDoesNotPersistFakeAnswer() {
        when(messageRepository.findBySessionIdOrderByCreatedAtDesc(eq(SESSION_ID), any())).thenReturn(List.of());
        when(aiProvider.converse(anyString(), anyList(), anyString()))
                .thenThrow(new AiProviderException("claude", new RuntimeException("boom")));

        assertThatThrownBy(() -> engine.converse(request("hi")))
                .isInstanceOf(ConversationFailedException.class);

        // Only the user's turn was persisted — no fabricated BooKI reply.
        verify(messageRepository, times(1)).save(savedMessageCaptor.capture());
        assertThat(savedMessageCaptor.getValue().getSpeaker()).isEqualTo(Message.Speaker.USER);
    }

    @Test
    void routesToCapabilityWhenModelEmitsDirective() {
        when(messageRepository.findBySessionIdOrderByCreatedAtDesc(eq(SESSION_ID), any())).thenReturn(List.of());
        when(aiProvider.converse(anyString(), anyList(), anyString())).thenReturn("{\"capability\":\"quiz\"}");
        ConversationCapability quiz = capability("quiz", "What is the capital of France?");
        when(capabilityRegistry.parseDirective("{\"capability\":\"quiz\"}")).thenReturn(Optional.of("quiz"));
        when(capabilityRegistry.find("quiz")).thenReturn(Optional.of(quiz));

        ConversationResult result = engine.converse(request("quiz me"));

        assertThat(result.botMessage().getMessage()).isEqualTo("What is the capital of France?");
    }

    @Test
    void explicitHintRunsCapabilityAndSkipsRoutingCall() {
        when(messageRepository.findBySessionIdOrderByCreatedAtDesc(eq(SESSION_ID), any())).thenReturn(List.of());
        ConversationCapability summary = capability("summary", "Here is your recap.");
        when(capabilityRegistry.find("summary")).thenReturn(Optional.of(summary));

        ConversationResult result = engine.converse(new ConversationRequest(
                USER_ID, SESSION_ID, "Summarize", Message.InputType.TEXT, "summary", 1, 3));

        assertThat(result.botMessage().getMessage()).isEqualTo("Here is your recap.");
        verify(aiProvider, never()).converse(anyString(), anyList(), anyString());
        verify(capabilityRegistry, never()).routerInstructions(any());
    }

    @Test
    void unknownHintIsRejected() {
        when(messageRepository.findBySessionIdOrderByCreatedAtDesc(eq(SESSION_ID), any())).thenReturn(List.of());
        when(capabilityRegistry.find("bogus")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> engine.converse(new ConversationRequest(
                USER_ID, SESSION_ID, "do a thing", Message.InputType.TEXT, "bogus", 1, 3)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static ConversationCapability capability(String name, String reply) {
        return new ConversationCapability() {
            @Override public String name() {
                return name;
            }

            @Override public String modelDescription() {
                return name;
            }

            @Override public String execute(com.booki.conversation.capability.CapabilityInvocation invocation) {
                return reply;
            }
        };
    }

    private static Message message(Message.Speaker speaker, String text) {
        Message m = new Message();
        m.setSpeaker(speaker);
        m.setInputType(Message.InputType.TEXT);
        m.setMessage(text);
        return m;
    }
}
