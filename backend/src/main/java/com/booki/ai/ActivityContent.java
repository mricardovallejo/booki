package com.booki.ai;

/**
 * What to actually send the model for one page range — resolved once by
 * {@link ActivityContentService} and shared by every consumer (quiz, summary,
 * chat) so there is exactly one place that decides between the two.
 */
public sealed interface ActivityContent {

    /** A document-capable provider: reads the whole uploaded PDF, told which pages this turn is about. */
    record DocumentReference(String fileId, int startPage, int endPage) implements ActivityContent {
    }

    /** Fallback for a provider with no document support (Kimi, Ollama): plain text extracted from just this range. */
    record PlainText(String text) implements ActivityContent {
    }
}
