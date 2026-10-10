package com.booki.ai;

import com.booki.domain.Document;
import com.booki.domain.PageMode;
import com.booki.domain.PageReference;
import com.booki.domain.PaginationStatus;
import com.booki.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * The single place every activity (chat, quiz, summary, session creation)
 * converts a reader-supplied page range into physical pages — centralizes
 * what used to be three separate inline clamps, and is where editorial page
 * numbers get converted using the document's {@code paginationOffset}. See
 * ADR-030.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PageRangeResolver {

    private final DocumentRepository documentRepository;
    private final PaginationDetectionService paginationDetectionService;

    public record PhysicalRange(int start, int end) {
    }

    public PhysicalRange resolve(Document document, Integer start, Integer end, PageMode mode) {
        if (start == null || end == null) {
            throw new IllegalArgumentException("startPage and endPage are required");
        }

        int rawStart = start;
        int rawEnd = end;
        if (mode == PageMode.EDITORIAL) {
            Integer offset = resolveOffset(document);
            if (offset != null) {
                rawStart = PageReference.toPhysical(start, offset);
                rawEnd = PageReference.toPhysical(end, offset);
            } else {
                log.warn("Editorial page range requested for document {} with no known pagination offset — "
                        + "treating {}..{} as physical pages", document.getId(), start, end);
            }
        }

        int pageCount = document.getPageCount();
        int clampedEnd = Math.max(1, Math.min(rawEnd, pageCount));
        int clampedStart = Math.max(1, Math.min(rawStart, clampedEnd));
        return new PhysicalRange(clampedStart, clampedEnd);
    }

    /**
     * The document's known offset, triggering a one-time auto-detect-and-cache
     * when nobody has ever set or detected one yet. Once a value is {@code
     * MANUAL} or {@code AUTO_DETECTED} (including a failed detection, recorded
     * as {@code DETECTION_FAILED}), this never runs again for that document —
     * only the explicit "detect pagination" action can replace it.
     */
    private Integer resolveOffset(Document document) {
        if (document.getPaginationStatus() != PaginationStatus.UNKNOWN) {
            return document.getPaginationOffset();
        }
        PaginationDetectionService.Result result = paginationDetectionService.detect(document);
        document.setPaginationOffset(result.offset());
        document.setPaginationStatus(result.detected() ? PaginationStatus.AUTO_DETECTED : PaginationStatus.DETECTION_FAILED);
        documentRepository.save(document);
        return result.offset();
    }
}
