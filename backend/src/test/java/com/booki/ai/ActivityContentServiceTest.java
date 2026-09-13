package com.booki.ai;

import com.booki.domain.Document;
import com.booki.storage.StorageAdapter;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.text.PDFTextStripper;
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
 * Regression coverage for the core promise: a document-capable provider gets
 * ONLY the pages the current turn's range asked for, physically cut from the
 * book — never the whole thing. Verified by re-parsing the bytes actually
 * handed to the provider, not by inspecting internal state.
 */
class ActivityContentServiceTest {

    private static final String FULL_BOOK_TITLE = "A Ten Page Geography Book";

    @Test
    void documentCapableProviderReceivesOnlyTheRequestedPagesNotTheWholeBook() throws IOException {
        byte[] tenPages = pdfWithPages(10);
        StorageAdapter storage = mock(StorageAdapter.class);
        when(storage.get(eq("documents/geo.pdf"))).thenReturn(new ByteArrayResource(tenPages));
        Document document = new Document();
        document.setTitle(FULL_BOOK_TITLE);
        document.setFilePath("documents/geo.pdf");
        document.setPageCount(10);

        ActivityContentService service = new ActivityContentService(storage);
        RecordingDocumentProvider provider = new RecordingDocumentProvider();

        ActivityContent content = service.resolve(document, provider, 4, 6);

        assertThat(content).isInstanceOf(ActivityContent.DocumentReference.class);
        byte[] sent = ((ActivityContent.DocumentReference) content).pdfBytes();

        // The slice is a real, independently loadable PDF...
        try (PDDocument sliced = Loader.loadPDF(sent)) {
            assertThat(sliced.getNumberOfPages()).isEqualTo(3); // pages 4, 5, 6
            String text = new PDFTextStripper().getText(sliced);
            assertThat(text).contains("page 4", "page 5", "page 6");
            assertThat(text).doesNotContain("page 1", "page 2", "page 3", "page 7", "page 8", "page 9", "page 10");
        }
        // ...and it is genuinely smaller than the whole book, not the same bytes relabeled.
        assertThat(sent.length).isLessThan(tenPages.length);
    }

    @Test
    void requestingTheSameRangeTwiceSlicesFreshEachTimeRatherThanCachingAReference() throws IOException {
        byte[] threePages = pdfWithPages(3);
        StorageAdapter storage = mock(StorageAdapter.class);
        when(storage.get(eq("documents/x.pdf"))).thenAnswer(inv -> new ByteArrayResource(threePages));
        Document document = new Document();
        document.setTitle("Book");
        document.setFilePath("documents/x.pdf");
        document.setPageCount(3);

        ActivityContentService service = new ActivityContentService(storage);
        RecordingDocumentProvider provider = new RecordingDocumentProvider();

        byte[] first = ((ActivityContent.DocumentReference) service.resolve(document, provider, 1, 1)).pdfBytes();
        byte[] second = ((ActivityContent.DocumentReference) service.resolve(document, provider, 1, 1)).pdfBytes();

        // Same requested range -> no shared object (freshly cut both times, no
        // upload/id caching left anywhere for this to reuse across calls) —
        // and equivalent CONTENT (PDFBox stamps a fresh creation timestamp into
        // each save, so the raw bytes legitimately differ even for identical pages).
        assertThat(first).isNotSameAs(second);
        try (PDDocument a = Loader.loadPDF(first); PDDocument b = Loader.loadPDF(second)) {
            assertThat(a.getNumberOfPages()).isEqualTo(b.getNumberOfPages()).isEqualTo(1);
            assertThat(new PDFTextStripper().getText(a)).isEqualTo(new PDFTextStripper().getText(b));
        }
    }

    @Test
    void aProviderWithoutDocumentSupportGetsPlainTextOfJustTheRange() {
        byte[] threePages = pdfWithPages(3);
        StorageAdapter storage = mock(StorageAdapter.class);
        when(storage.get(eq("documents/x.pdf"))).thenReturn(new ByteArrayResource(threePages));
        Document document = new Document();
        document.setTitle("Book");
        document.setFilePath("documents/x.pdf");
        document.setPageCount(3);

        ActivityContentService service = new ActivityContentService(storage);
        AiProvider textOnlyProvider = new AiProvider() {
            @Override public String converse(String systemPrompt, List<Message> context, String userMessage) {
                return "n/a";
            }
            @Override public String model() {
                return "fake";
            }
            @Override public String key() {
                return "kimi";
            }
        };

        ActivityContent content = service.resolve(document, textOnlyProvider, 2, 2);

        assertThat(content).isInstanceOf(ActivityContent.PlainText.class);
        String text = ((ActivityContent.PlainText) content).text();
        assertThat(text).contains("page 2").doesNotContain("page 1", "page 3");
    }

    /** A fake AiProvider that supports documents, so resolve() takes the slicing branch. */
    private static final class RecordingDocumentProvider implements AiProvider {
        @Override public String converse(String systemPrompt, List<Message> context, String userMessage) {
            return "n/a";
        }
        @Override public String model() {
            return "fake";
        }
        @Override public String key() {
            return "claude";
        }
        @Override public boolean supportsDocuments() {
            return true;
        }
        @Override public String converseWithDocument(String systemPrompt, List<Message> context, String userMessage,
                                                      byte[] pdfBytes, int startPage, int endPage) {
            return "n/a";
        }
    }

    private static byte[] pdfWithPages(int count) {
        try (PDDocument document = new PDDocument()) {
            for (int i = 1; i <= count; i++) {
                PDPage page = new PDPage();
                document.addPage(page);
                try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                    content.beginText();
                    content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    content.newLineAtOffset(50, 700);
                    content.showText("This is page " + i + " of the book.");
                    content.endText();
                }
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
