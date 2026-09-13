package com.booki.ai;

/**
 * What to actually send the model for one page range — resolved once by
 * {@link ActivityContentService} and shared by every consumer (quiz, summary,
 * chat) so there is exactly one place that decides between the two.
 */
public sealed interface ActivityContent {

    /**
     * A document-capable provider: reads {@code pdfBytes} directly — physically
     * just the {@code startPage}..{@code endPage} slice cut from the original
     * PDF, never the whole book, so cost/latency scale with the range size.
     */
    record DocumentReference(byte[] pdfBytes, int startPage, int endPage) implements ActivityContent {
    }

    /** Fallback for a provider with no document support (Kimi, Ollama): plain text extracted from just this range. */
    record PlainText(String text) implements ActivityContent {
    }
}
