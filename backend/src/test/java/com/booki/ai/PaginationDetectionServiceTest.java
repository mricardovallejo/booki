package com.booki.ai;

import com.booki.domain.Document;
import com.booki.storage.StorageAdapter;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Verifies the offset arithmetic and degrade-gracefully behavior of pagination
 * detection — not a real AI call, a scripted {@link AiProvider} standing in
 * for one. See ADR-030.
 */
class PaginationDetectionServiceTest {

    @Test
    void parsesAnOffsetReplyIntoThePhysicalPageMinusOne() {
        PaginationDetectionService service = serviceWithReply("OFFSET: 3");

        PaginationDetectionService.Result result = service.detect(document(10));

        // Physical page 3 is where printed page 1 appears, so the stored offset is 2.
        assertThat(result.detected()).isTrue();
        assertThat(result.offset()).isEqualTo(2);
    }

    @Test
    void zeroOffsetMeansPrintedPageOneIsPhysicalPageOne() {
        PaginationDetectionService service = serviceWithReply("OFFSET: 1");

        PaginationDetectionService.Result result = service.detect(document(5));

        assertThat(result.detected()).isTrue();
        assertThat(result.offset()).isZero();
    }

    @Test
    void noneReplyDegradesToNotDetectedRatherThanThrowing() {
        PaginationDetectionService service = serviceWithReply("OFFSET: NONE");

        PaginationDetectionService.Result result = service.detect(document(5));

        assertThat(result.detected()).isFalse();
        assertThat(result.offset()).isNull();
    }

    @Test
    void unparseableReplyDegradesToNotDetectedRatherThanThrowing() {
        PaginationDetectionService service = serviceWithReply("I'm not sure, sorry.");

        PaginationDetectionService.Result result = service.detect(document(5));

        assertThat(result.detected()).isFalse();
        assertThat(result.offset()).isNull();
    }

    @Test
    void providerFailureDegradesToNotDetectedRatherThanPropagating() {
        AiProvider throwing = new AiProvider() {
            @Override public String converse(String systemPrompt, List<Message> context, String userMessage) {
                throw new AiProviderException("fake", new RuntimeException("boom"));
            }
            @Override public String model() {
                return "fake-model";
            }
            @Override public String key() {
                return "fake";
            }
        };
        PaginationDetectionService service = service(throwing);

        PaginationDetectionService.Result result = service.detect(document(5));

        assertThat(result.detected()).isFalse();
        assertThat(result.offset()).isNull();
    }

    private PaginationDetectionService serviceWithReply(String reply) {
        AiProvider provider = new AiProvider() {
            @Override public String converse(String systemPrompt, List<Message> context, String userMessage) {
                return reply;
            }
            @Override public String model() {
                return "fake-model";
            }
            @Override public String key() {
                return "fake";
            }
        };
        return service(provider);
    }

    private PaginationDetectionService service(AiProvider provider) {
        StorageAdapter storage = mock(StorageAdapter.class);
        when(storage.get(eq("documents/book.pdf"))).thenAnswer(inv -> new ByteArrayResource(tinyPdf()));
        ActivityContentService activityContentService = new ActivityContentService(storage);

        AiProviderRegistry registry = mock(AiProviderRegistry.class);
        when(registry.get(null)).thenReturn(provider);

        return new PaginationDetectionService(activityContentService, registry);
    }

    private Document document(int pageCount) {
        Document document = new Document();
        document.setId(1L);
        document.setTitle("Book");
        document.setFilePath("documents/book.pdf");
        document.setPageCount(pageCount);
        return document;
    }

    private static byte[] tinyPdf() {
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage());
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
