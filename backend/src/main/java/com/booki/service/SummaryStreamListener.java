package com.booki.service;

import com.booki.dto.SentReportResponse;

/**
 * Receives a summary as it is written. Transport-neutral: the service knows
 * nothing about SSE. Runs on the request thread, and each stream ends in exactly
 * one of {@link #onDone} or {@link #onFailed}.
 */
public interface SummaryStreamListener {

    /** Everything was validated and the model call is starting. Errors before this are thrown instead. */
    void onStart();

    void onDelta(String text);

    /** The text is complete, and the book's figures are being cut before saving it. */
    void onFigures();

    /**
     * The summary is saved as a chat message, so later turns know about it.
     *
     * @param cutOff      the text is incomplete; it also ends with a visible notice saying so
     * @param report      the PDF, if one was requested and could be built
     * @param reportError why a requested PDF could not be built, else null
     */
    void onDone(Long messageId, boolean cutOff, SentReportResponse report, String reportError);

    /** Nothing was written, and nothing was saved. */
    void onFailed(String message);
}
