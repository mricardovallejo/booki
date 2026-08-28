package com.booki.conversation;

import com.booki.domain.Message;

/**
 * Outcome of one conversational turn: the persisted user message and BooKI's
 * persisted reply. Returned as domain entities so each transport adapter maps
 * to its own response shape (REST {@code MessageResponse} today).
 *
 * <p>Deliberately a record with room to grow — later phases add fields such as
 * an invoked capability or a TTS audio handle without changing callers that
 * only read {@link #botMessage()}.
 */
public record ConversationResult(
        Message userMessage,
        Message botMessage) {
}
