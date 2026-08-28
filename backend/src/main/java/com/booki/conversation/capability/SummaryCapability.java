package com.booki.conversation.capability;

import com.booki.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * "Summarize these pages before I continue." Reuses
 * {@link ReportService#generateSummaryText} — the same generator behind the
 * Summary modal / {@code POST /summary} — and passes the reader's own message
 * through as the custom request, so "summarize just the part about X" works.
 * The engine persists the returned text as a normal BooKI message.
 */
@Component
@RequiredArgsConstructor
public class SummaryCapability implements ConversationCapability {

    /** Conversational summaries stay short; the modal is where length is configurable. */
    private static final int DEFAULT_LENGTH_PAGES = 2;

    private final ReportService reportService;

    @Override
    public String name() {
        return "summary";
    }

    @Override
    public String modelDescription() {
        return "summary — write a concise recap of the session's page range and the discussion so far; "
                + "use when the reader asks for a summary, recap, or overview";
    }

    @Override
    public String execute(CapabilityInvocation invocation) {
        return reportService.generateSummaryText(invocation.session(), DEFAULT_LENGTH_PAGES, invocation.userText());
    }
}
