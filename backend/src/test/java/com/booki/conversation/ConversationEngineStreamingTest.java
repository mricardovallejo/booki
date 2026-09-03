package com.booki.conversation;

import com.booki.ai.AiProviderException;
import com.booki.ai.AiProviderRegistry;
import com.booki.ai.StreamingAiProvider;
import com.booki.conversation.capability.CapabilityInvocation;
import com.booki.conversation.capability.CapabilityRegistry;
import com.booki.conversation.capability.ConversationCapability;
import com.booki.domain.Document;
import com.booki.domain.Message;
import com.booki.domain.Session;
import com.booki.repository.DocumentPageRepository;
import com.booki.repository.MessageRepository;
import com.booki.repository.SessionRepository;
import com.booki.domain.Capability;
import com.booki.prompt.PromptAssembler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.EnumSet;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConversationEngineStreamingTest {

    private static final long USER_ID = 7L;
    private static final long SESSION_ID = 1L;

    @Mock private SessionRepository sessionRepository;
    @Mock private MessageRepository messageRepository;
    @Mock private DocumentPageRepository documentPageRepository;
    @Mock private AiProviderRegistry aiProviderRegistry;
    @Mock private PromptAssembler promptAssembler;
    @Mock private CapabilityRegistry capabilityRegistry;
    @Mock private Session session;
    @Mock private Document document;

    private ConversationEngine engine;

    @BeforeEach
    void setUp() {
        engine = new ConversationEngine(sessionRepository, messageRepository, documentPageRepository,
                aiProviderRegistry, promptAssembler, capabilityRegistry, 20, 24000);

        when(sessionRepository.findByIdAndUserId(SESSION_ID, USER_ID)).thenReturn(Optional.of(session));
        lenient().when(session.getId()).thenReturn(SESSION_ID);
        lenient().when(session.getDocument()).thenReturn(document);
        lenient().when(session.getStartPage()).thenReturn(1);
        lenient().when(session.getEndPage()).thenReturn(3);
        lenient().when(session.getAiProvider()).thenReturn("claude");
        lenient().when(document.getId()).thenReturn(42L);

        when(messageRepository.save(any(Message.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(messageRepository.findBySessionIdOrderByCreatedAtDesc(eq(SESSION_ID), any())).thenReturn(List.of());
        lenient().when(documentPageRepository.findByDocumentIdAndPageNumberBetweenOrderByPageNumberAsc(any(), any(), any()))
                .thenReturn(List.of());
        lenient().when(promptAssembler.forChat(any(), anyString())).thenReturn("system-prompt");
        lenient().when(promptAssembler.enabledCapabilities(any())).thenReturn(EnumSet.allOf(Capability.class));
        lenient().when(capabilityRegistry.routerInstructions(any())).thenReturn("");
        lenient().when(capabilityRegistry.maxDirectiveLength()).thenReturn(160);
    }

    private void providerEmits(String... events) {
        doAnswer(inv -> {
            StreamingAiProvider.TokenStream ts = inv.getArgument(4);
            StringBuilder full = new StringBuilder();
            for (String e : events) {
                full.append(e);
                ts.onDelta(e);
            }
            ts.onComplete(full.toString());
            return null;
        }).when(aiProviderRegistry).converseStreaming(any(), anyString(), anyList(), anyString(), any());
    }

    @Test
    void streamsPlainChatDeltasThenPersistsTheBotMessage() {
        when(capabilityRegistry.isEmpty()).thenReturn(true);
        providerEmits("Hello", " there");

        RecordingStream out = new RecordingStream();
        engine.converseStreaming(new ConversationRequest(USER_ID, SESSION_ID, "hi", Message.InputType.TEXT), out);

        assertThat(out.deltas).containsExactly("Hello", " there");
        assertThat(out.completed).isNotNull();
        assertThat(out.completed.botMessage().getMessage()).isEqualTo("Hello there");
        verify(messageRepository, times(2)).save(any(Message.class)); // user + bot
    }

    @Test
    void withholdsOutputUntilAReplyCannotBeADirectiveThenFlushesLive() {
        when(capabilityRegistry.isEmpty()).thenReturn(false);
        when(capabilityRegistry.parseDirective(anyString())).thenReturn(Optional.empty());
        providerEmits("{oops not json} rest of the answer keeps going well beyond the directive length cap so it flushes");

        RecordingStream out = new RecordingStream();
        engine.converseStreaming(new ConversationRequest(USER_ID, SESSION_ID, "hi", Message.InputType.TEXT), out);

        assertThat(String.join("", out.deltas)).startsWith("{oops not json}");
        assertThat(out.completed.botMessage().getMessage()).startsWith("{oops not json}");
    }

    @Test
    void routesToCapabilityWithoutEverStreamingTheDirectiveText() {
        when(capabilityRegistry.isEmpty()).thenReturn(false);
        when(capabilityRegistry.parseDirective("{\"capability\":\"quiz\"}")).thenReturn(Optional.of("quiz"));
        when(capabilityRegistry.find("quiz")).thenReturn(Optional.of(capability("quiz", "What is X?")));
        providerEmits("{\"capability\":", "\"quiz\"}");

        RecordingStream out = new RecordingStream();
        engine.converseStreaming(new ConversationRequest(USER_ID, SESSION_ID, "quiz me", Message.InputType.TEXT), out);

        assertThat(out.deltas).containsExactly("What is X?");
        assertThat(out.deltas).noneMatch(d -> d.contains("{"));
        assertThat(out.completed.botMessage().getMessage()).isEqualTo("What is X?");
    }

    @Test
    void explicitHintEmitsCapabilityAsOneDeltaAndSkipsTheStreamingCall() {
        when(capabilityRegistry.find("summary")).thenReturn(Optional.of(capability("summary", "Your recap.")));

        RecordingStream out = new RecordingStream();
        engine.converseStreaming(
                new ConversationRequest(USER_ID, SESSION_ID, "Summarize", Message.InputType.TEXT, "summary"), out);

        assertThat(out.deltas).containsExactly("Your recap.");
        assertThat(out.completed.botMessage().getMessage()).isEqualTo("Your recap.");
        verify(aiProviderRegistry, never()).converseStreaming(any(), anyString(), anyList(), anyString(), any());
    }

    @Test
    void streamErrorBecomesConversationFailedAndNoBotMessageIsPersisted() {
        when(capabilityRegistry.isEmpty()).thenReturn(true);
        doAnswer(inv -> {
            StreamingAiProvider.TokenStream ts = inv.getArgument(4);
            ts.onError(new AiProviderException("claude", new RuntimeException("boom")));
            return null;
        }).when(aiProviderRegistry).converseStreaming(any(), anyString(), anyList(), anyString(), any());

        RecordingStream out = new RecordingStream();
        engine.converseStreaming(new ConversationRequest(USER_ID, SESSION_ID, "hi", Message.InputType.TEXT), out);

        assertThat(out.error).isInstanceOf(ConversationFailedException.class);
        assertThat(out.completed).isNull();
        verify(messageRepository, times(1)).save(any(Message.class)); // user only
    }

    private static ConversationCapability capability(String name, String reply) {
        return new ConversationCapability() {
            @Override public String name() {
                return name;
            }

            @Override public String modelDescription() {
                return name;
            }

            @Override public String execute(CapabilityInvocation invocation) {
                return reply;
            }
        };
    }

    private static final class RecordingStream implements ConversationStream {
        final List<String> deltas = new ArrayList<>();
        ConversationResult completed;
        RuntimeException error;

        @Override public void onDelta(String text) {
            deltas.add(text);
        }

        @Override public void onComplete(ConversationResult result) {
            completed = result;
        }

        @Override public void onError(RuntimeException e) {
            error = e;
        }
    }
}
