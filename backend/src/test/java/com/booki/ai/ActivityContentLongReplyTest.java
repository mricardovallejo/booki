package com.booki.ai;

import com.booki.storage.StorageAdapter;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class ActivityContentLongReplyTest {

    private final ClaudeProvider provider = mock(ClaudeProvider.class);
    private final ActivityContentService service = new ActivityContentService(mock(StorageAdapter.class));
    private final ActivityContent content = new ActivityContent.DocumentReference(new byte[] {1}, 1, 3);

    private void whenStreaming(java.util.function.Consumer<StreamingAiProvider.TokenStream> behavior) {
        doAnswer(call -> {
            behavior.accept(call.getArgument(4));
            return null;
        }).when(provider).converseLongStream(any(), any(), any(), any(), any());
    }

    @Test
    void aFinishedTextComesFromTheLongStreamNotTheShortBlockingCall() {
        whenStreaming(stream -> {
            stream.onDelta("Long ");
            stream.onComplete("Long notes");
        });

        String reply = service.converseLong(provider, content, "system", List.of(), "user");

        assertThat(reply).isEqualTo("Long notes");
        verify(provider, never()).converseWithDocument(any(), any(), any(), any(), org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void aStreamFailureReachesTheCaller() {
        AiProviderException failure = new AiProviderException("claude", "overloaded", null);
        whenStreaming(stream -> stream.onError(failure));

        assertThatThrownBy(() -> service.converseLong(provider, content, "system", List.of(), "user"))
                .isSameAs(failure);
    }
}
