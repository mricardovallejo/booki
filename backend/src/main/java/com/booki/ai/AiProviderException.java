package com.booki.ai;

/**
 * Raised when an {@link AiProvider} cannot produce a genuine model response —
 * network failure, upstream 4xx/5xx, or an empty/unparseable payload.
 *
 * <p>Providers used to swallow these and return canned apology text, which was
 * indistinguishable from a real answer and got persisted as a normal BooKI
 * message. They now throw this instead; the caller (typically
 * {@code ConversationEngine}) decides how to surface it to the user.
 */
public class AiProviderException extends RuntimeException {

    private final String provider;

    public AiProviderException(String provider, String message, Throwable cause) {
        super("[" + provider + "] " + message, cause);
        this.provider = provider;
    }

    public AiProviderException(String provider, Throwable cause) {
        this(provider, "request failed", cause);
    }

    public String getProvider() {
        return provider;
    }
}
