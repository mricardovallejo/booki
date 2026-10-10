package com.booki.ai;

import com.booki.domain.Document;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds the constant offset between a document's physical PDF pages and its
 * printed/editorial page numbers, by asking the app's default AI provider to
 * look at the first few pages. One call per invocation — callers (see
 * {@link PageRangeResolver}, {@code DocumentController}) are responsible for
 * running this at most once per document and caching the result (ADR-030).
 *
 * <p>Deliberately reuses {@link ActivityContentService#resolve}/{@code converse}
 * exactly as any other activity does (ADR-025) — a document-capable provider
 * gets the real sliced pages (handles scanned/image pages), anything else
 * gets plain extracted text. No separate extraction path.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaginationDetectionService {

    /** First N physical pages are enough to find the start of printed pagination for any normal book. */
    private static final int SAMPLE_PAGES = 20;

    private static final Pattern OFFSET_PATTERN = Pattern.compile("OFFSET:\\s*(-?\\d+|NONE)", Pattern.CASE_INSENSITIVE);

    private final ActivityContentService activityContentService;
    private final AiProviderRegistry aiProviderRegistry;

    public record Result(Integer offset, boolean detected) {
    }

    public Result detect(Document document) {
        AiProvider provider = aiProviderRegistry.get(null);
        int sampleEnd = Math.min(SAMPLE_PAGES, document.getPageCount());
        ActivityContent content = activityContentService.resolve(document, provider, 1, sampleEnd);

        String systemPrompt = "You are inspecting the opening pages of a book to find its printed pagination.";
        String instruction = "Here are the first pages of a book, in physical PDF order starting at physical page 1. "
                + "Many books place front matter (cover, title page, table of contents) before the page printed "
                + "with the number \"1\" — find the physical page on which that printed page \"1\" appears. "
                + "Respond with exactly one line, nothing else: `OFFSET: <integer>` (the physical page number of "
                + "printed page 1) or `OFFSET: NONE` if no reliable printed page numbering is visible.";

        String reply;
        try {
            reply = activityContentService.converse(provider, content, systemPrompt, List.of(), instruction);
        } catch (RuntimeException e) {
            log.warn("Pagination detection failed for document {}: {}", document.getId(), e.toString());
            return new Result(null, false);
        }

        Matcher matcher = OFFSET_PATTERN.matcher(reply);
        if (!matcher.find() || "NONE".equalsIgnoreCase(matcher.group(1))) {
            log.warn("Pagination detection found no reliable offset for document {}", document.getId());
            return new Result(null, false);
        }
        try {
            // The offset is a physical page number, at least 1 (can't precede the PDF itself).
            int offset = Math.max(0, Integer.parseInt(matcher.group(1)) - 1);
            return new Result(offset, true);
        } catch (NumberFormatException e) {
            log.warn("Pagination detection reply did not parse for document {}: {}", document.getId(), reply);
            return new Result(null, false);
        }
    }
}
