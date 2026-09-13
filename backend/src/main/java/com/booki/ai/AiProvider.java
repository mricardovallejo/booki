package com.booki.ai;

import java.util.List;

public interface AiProvider {
    String converse(String systemPrompt, List<Message> context, String userMessage);

    /** The concrete model name this provider is configured to call — for logging only. */
    String model();

    /** Stable identifier ("claude", "openai", "kimi", "ollama") — matches the bean name in {@code AiProviderRegistry}. */
    String key();

    /** True if this provider can read a PDF directly (see {@link #converseWithDocument}). */
    default boolean supportsDocuments() {
        return false;
    }

    /**
     * Like {@link #converse}, but the model reads {@code pdfBytes} directly —
     * no text BooKI extracted is involved. {@code pdfBytes} is only ever the
     * pages in {@code startPage}..{@code endPage}, physically cut from the
     * original PDF ({@code ActivityContentService}) — never the whole book —
     * so cost and latency scale with the size of the range the reader picked,
     * not the size of the book.
     */
    default String converseWithDocument(String systemPrompt, List<Message> context, String userMessage,
                                         byte[] pdfBytes, int startPage, int endPage) {
        throw new UnsupportedOperationException(key() + " does not support document input");
    }

    record Message(String role, String content) {
    }
}
