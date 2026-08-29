package com.booki.ai;

import java.util.List;

/**
 * Optional companion to {@link AiProvider}: a provider that can emit its reply
 * incrementally implements this <em>in addition to</em> {@code AiProvider}.
 * {@code converse()} is never removed or changed — callers that don't need
 * streaming keep using it, and a provider that can't stream simply doesn't
 * implement this (see {@code AiProviderRegistry.converseStreaming}, which
 * bridges).
 *
 * <p>The callback is deliberately library-neutral (no Reactor / Flux in the
 * signature) so the domain layer stays uncoupled from any transport or
 * streaming library. A provider impl may use WebClient/Reactor internally.
 *
 * <p>Contract: {@code converseStream} never throws. It always terminates the
 * {@link TokenStream} exactly once — via {@code onComplete} on success or
 * {@code onError} on failure — even if some deltas were already delivered.
 */
public interface StreamingAiProvider {

    void converseStream(String systemPrompt, List<AiProvider.Message> context,
                        String userMessage, TokenStream stream);

    interface TokenStream {
        /** A chunk of reply text, in order. May be called zero or more times. */
        void onDelta(String text);

        /** Terminal success. {@code fullText} is the concatenation of every delta. */
        void onComplete(String fullText);

        /** Terminal failure (typically an {@link AiProviderException}). */
        void onError(RuntimeException error);
    }
}
