package com.booki.ai;

import java.util.List;

public interface AiProvider {
    String converse(String systemPrompt, List<Message> context, String userMessage);

    /** The concrete model name this provider is configured to call — for logging only. */
    String model();

    /** Stable identifier ("claude", "openai", "kimi", "ollama") — matches the bean name in {@code AiProviderRegistry}. */
    String key();

    /** True if this provider can read an uploaded PDF directly (see {@link #uploadDocument} / {@link #converseWithDocument}). */
    default boolean supportsDocuments() {
        return false;
    }

    /**
     * Uploads {@code pdfBytes} to this provider's file storage, returning an id
     * to reuse on every later {@link #converseWithDocument} call for the same
     * document — callers upload once and cache the id (see
     * {@code ActivityContentService}), never per turn.
     */
    default String uploadDocument(byte[] pdfBytes, String title) {
        throw new UnsupportedOperationException(key() + " does not support document uploads");
    }

    /**
     * Like {@link #converse}, but the model reads the previously uploaded PDF
     * ({@code fileId}) directly — no text BooKI extracted is involved.
     * {@code startPage}/{@code endPage} are conveyed to the model as an
     * instruction ("use only these pages"), not a hard restriction: the model
     * has the whole document, the same as a person handed the whole book and
     * asked to focus on one chapter.
     */
    default String converseWithDocument(String systemPrompt, List<Message> context, String userMessage,
                                         String fileId, int startPage, int endPage) {
        throw new UnsupportedOperationException(key() + " does not support document uploads");
    }

    record Message(String role, String content) {
    }
}
