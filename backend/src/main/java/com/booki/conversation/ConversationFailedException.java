package com.booki.conversation;

/**
 * A conversational turn could not be completed for a reason the user should be
 * told about in plain language (typically an underlying
 * {@link com.booki.ai.AiProviderException}). {@code GlobalExceptionHandler}
 * turns this into a controlled HTTP error instead of persisting a fake answer.
 */
public class ConversationFailedException extends RuntimeException {

    public ConversationFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
