package com.booki.ai;

import com.booki.domain.Document;
import com.booki.domain.PageMode;
import com.booki.domain.PaginationStatus;
import com.booki.repository.DocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The one place physical/editorial conversion and range clamping happen. See
 * ADR-030.
 */
@ExtendWith(MockitoExtension.class)
class PageRangeResolverTest {

    @Mock private DocumentRepository documentRepository;
    @Mock private PaginationDetectionService paginationDetectionService;

    private PageRangeResolver resolver;
    private Document document;

    @BeforeEach
    void setUp() {
        resolver = new PageRangeResolver(documentRepository, paginationDetectionService);
        document = new Document();
        document.setId(1L);
        document.setPageCount(20);
    }

    @Test
    void physicalModeJustClampsWithoutTouchingPaginationAtAll() {
        PageRangeResolver.PhysicalRange range = resolver.resolve(document, 5, 10, PageMode.PHYSICAL);

        assertThat(range.start()).isEqualTo(5);
        assertThat(range.end()).isEqualTo(10);
        verify(paginationDetectionService, never()).detect(document);
    }

    @Test
    void invertedOrOutOfBoundsPagesAreClampedNotRejected() {
        assertThat(resolver.resolve(document, 10, 3, PageMode.PHYSICAL)).isEqualTo(new PageRangeResolver.PhysicalRange(3, 3));
        assertThat(resolver.resolve(document, 1, 99, PageMode.PHYSICAL)).isEqualTo(new PageRangeResolver.PhysicalRange(1, 20));
    }

    @Test
    void nullBoundsAreRejected() {
        assertThatThrownBy(() -> resolver.resolve(document, null, 3, PageMode.PHYSICAL))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void editorialModeConvertsUsingAKnownManualOffset() {
        document.setPaginationOffset(2);
        document.setPaginationStatus(PaginationStatus.MANUAL);

        PageRangeResolver.PhysicalRange range = resolver.resolve(document, 1, 3, PageMode.EDITORIAL);

        assertThat(range.start()).isEqualTo(3); // editorial 1 + offset 2
        assertThat(range.end()).isEqualTo(5);   // editorial 3 + offset 2
        verify(paginationDetectionService, never()).detect(document);
    }

    @Test
    void editorialModeTriggersDetectionExactlyOnceWhenOffsetIsUnknown() {
        document.setPaginationStatus(PaginationStatus.UNKNOWN);
        when(paginationDetectionService.detect(document))
                .thenReturn(new PaginationDetectionService.Result(2, true));
        clearInvocations(paginationDetectionService); // the when(...) setup itself counts as a call

        PageRangeResolver.PhysicalRange first = resolver.resolve(document, 1, 1, PageMode.EDITORIAL);
        assertThat(first.start()).isEqualTo(3);
        assertThat(document.getPaginationStatus()).isEqualTo(PaginationStatus.AUTO_DETECTED);
        assertThat(document.getPaginationOffset()).isEqualTo(2);
        verify(documentRepository).save(document);

        // Second call sees the now-cached status and never re-detects.
        clearInvocations(paginationDetectionService);
        resolver.resolve(document, 1, 1, PageMode.EDITORIAL);
        verify(paginationDetectionService, never()).detect(document);
    }

    @Test
    void editorialModeFallsBackToPhysicalWhenDetectionFailsAndRecordsThatOutcome() {
        document.setPaginationStatus(PaginationStatus.UNKNOWN);
        when(paginationDetectionService.detect(document))
                .thenReturn(new PaginationDetectionService.Result(null, false));
        clearInvocations(paginationDetectionService); // the when(...) setup itself counts as a call

        PageRangeResolver.PhysicalRange range = resolver.resolve(document, 5, 7, PageMode.EDITORIAL);

        // No offset known -> the raw numbers are treated as physical pages, not rejected.
        assertThat(range.start()).isEqualTo(5);
        assertThat(range.end()).isEqualTo(7);
        assertThat(document.getPaginationStatus()).isEqualTo(PaginationStatus.DETECTION_FAILED);

        // DETECTION_FAILED is a cached terminal outcome too — never retried automatically.
        clearInvocations(paginationDetectionService);
        resolver.resolve(document, 5, 7, PageMode.EDITORIAL);
        verify(paginationDetectionService, never()).detect(document);
    }

    @Test
    void manualOffsetIsNeverOverwrittenByTheLazyFallback() {
        document.setPaginationOffset(4);
        document.setPaginationStatus(PaginationStatus.MANUAL);

        resolver.resolve(document, 1, 1, PageMode.EDITORIAL);

        verify(paginationDetectionService, never()).detect(document);
        assertThat(document.getPaginationOffset()).isEqualTo(4);
        assertThat(document.getPaginationStatus()).isEqualTo(PaginationStatus.MANUAL);
    }
}
