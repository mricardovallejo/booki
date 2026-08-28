package com.booki.conversation;

import com.booki.domain.Message;

/**
 * A single inbound conversational turn, decoupled from how it arrived.
 *
 * <p>Text chat, quick-action buttons and (later) transcribed voice all build
 * one of these and hand it to {@link ConversationEngine}. The engine never
 * learns whether the transport was REST, SSE or a future WebSocket.
 *
 * @param capabilityHint optional capability name (e.g. {@code "summary"}) set by
 *                       a quick-action button. When present the engine runs that
 *                       capability directly, skipping the routing model call.
 *                       When {@code null} the model decides (see
 *                       {@code CapabilityRegistry}).
 */
public record ConversationRequest(
        Long userId,
        Long sessionId,
        String text,
        Message.InputType inputType,
        String capabilityHint) {

    public ConversationRequest {
        if (inputType == null) {
            inputType = Message.InputType.TEXT;
        }
    }

    /** Turn with no explicit capability hint — the model routes. */
    public ConversationRequest(Long userId, Long sessionId, String text, Message.InputType inputType) {
        this(userId, sessionId, text, inputType, null);
    }
}
