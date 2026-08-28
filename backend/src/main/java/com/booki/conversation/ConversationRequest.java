package com.booki.conversation;

import com.booki.domain.Message;

/**
 * A single inbound conversational turn, decoupled from how it arrived.
 *
 * <p>Text chat, quick-action buttons and (later) transcribed voice all build
 * one of these and hand it to {@link ConversationEngine}. The engine never
 * learns whether the transport was REST, SSE or a future WebSocket.
 */
public record ConversationRequest(
        Long userId,
        Long sessionId,
        String text,
        Message.InputType inputType) {

    public ConversationRequest {
        if (inputType == null) {
            inputType = Message.InputType.TEXT;
        }
    }
}
