package com.booki.conversation;

/**
 * Callback for a streaming conversational turn — the incremental counterpart of
 * {@link ConversationEngine#converse}. Domain-typed on purpose: {@code onComplete}
 * hands back a {@link ConversationResult} (the same type {@code converse()}
 * returns), so the engine's API stays transport-neutral. A future SSE / WebSocket
 * controller adapts this callback to its wire; the engine never learns which.
 *
 * <p>Terminated exactly once: {@code onComplete} on success, {@code onError} on
 * failure — even if some deltas were already delivered.
 */
public interface ConversationStream {

    /** A chunk of BooKI's reply text, in order. */
    void onDelta(String text);

    /** Terminal success. The user and bot messages are already persisted. */
    void onComplete(ConversationResult result);

    /** Terminal failure — typically a {@link ConversationFailedException}. */
    void onError(RuntimeException error);
}
