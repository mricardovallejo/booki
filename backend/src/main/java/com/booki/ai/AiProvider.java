package com.booki.ai;

import java.util.List;

public interface AiProvider {

    /**
     * Ends any reply the model didn't finish, so being cut off is never silent: the
     * reader sees it, the PDF shows it, and later turns can read it in the history.
     */
    String CUT_OFF_NOTICE = "\n\n---\n**This reply is incomplete: it was cut off before the end. "
            + "Ask BooKI to continue, or choose fewer pages.**";

    static boolean wasCutOff(String reply) {
        return reply != null && reply.endsWith(CUT_OFF_NOTICE);
    }

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

    /** True if this provider can look at an image (see {@link #describeImage}). */
    default boolean supportsImages() {
        return false;
    }

    /** One standalone question about a JPEG, with no conversation around it. */
    default String describeImage(String prompt, byte[] jpeg) {
        throw new UnsupportedOperationException(key() + " does not support image input");
    }

    record Message(String role, String content) {
    }
}
