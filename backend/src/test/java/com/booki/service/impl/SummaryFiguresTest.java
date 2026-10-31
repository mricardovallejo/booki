package com.booki.service.impl;

import com.booki.ai.AiProvider;
import com.booki.domain.Document;
import com.booki.domain.Session;
import com.booki.storage.StorageAdapter;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.io.ByteArrayResource;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SummaryFiguresTest {

    private static final String UUID_MARKER = "\\[\\[FIG:[0-9a-f-]{36}\\|%s]]";

    /** Answers with the given JSON, or throws when it is null. */
    private static class VisionStub implements AiProvider {
        final AtomicInteger calls = new AtomicInteger();
        private final String answer;

        VisionStub(String answer) {
            this.answer = answer;
        }

        @Override
        public String converse(String systemPrompt, List<Message> context, String userMessage) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String model() {
            return "stub";
        }

        @Override
        public String key() {
            return "stub";
        }

        @Override
        public boolean supportsImages() {
            return true;
        }

        @Override
        public String describeImage(String prompt, byte[] jpeg) {
            calls.incrementAndGet();
            if (answer == null) {
                throw new IllegalStateException("boom");
            }
            return "```json\n" + answer + "\n```";
        }
    }

    private StorageAdapter storage;
    private SummaryFigures figures;
    private Session session;

    @BeforeEach
    void setUp() throws IOException {
        storage = mock(StorageAdapter.class);
        when(storage.get("documents/book.pdf")).thenReturn(new ByteArrayResource(threePages()));
        figures = new SummaryFigures(storage);
        Document document = new Document();
        document.setFilePath("documents/book.pdf");
        session = new Session();
        session.setId(7L);
        session.setDocument(document);
    }

    private static byte[] threePages() throws IOException {
        try (PDDocument pdf = new PDDocument()) {
            pdf.addPage(new PDPage());
            pdf.addPage(new PDPage());
            pdf.addPage(new PDPage());
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            pdf.save(out);
            return out.toByteArray();
        }
    }

    private BufferedImage storedImage() throws IOException {
        ArgumentCaptor<byte[]> bytes = ArgumentCaptor.forClass(byte[].class);
        verify(storage).put(anyString(), bytes.capture(), eq("image/jpeg"));
        return ImageIO.read(new ByteArrayInputStream(bytes.getValue()));
    }

    @Test
    void aLocatedFigureIsCutInTwoLooksAndItsMarkerPointsAtTheImage() throws IOException {
        VisionStub provider = new VisionStub("{\"found\":true,\"left\":\"F\",\"top\":6,\"right\":\"J\",\"bottom\":10}");

        String result = figures.resolve(session, provider, "Before\n[[FIG:2|A map]]\nAfter", 1, 3);

        assertThat(result).matches("(?s)Before\n" + UUID_MARKER.formatted("A map") + "\nAfter");
        assertThat(provider.calls).hasValue(2);
        BufferedImage cut = storedImage();
        // Much smaller than the page: letter at 150 dpi is 1275 x 1650.
        assertThat(cut.getWidth()).isLessThan(1000);
        assertThat(cut.getHeight()).isLessThan(1000);
    }

    @Test
    void aFigureTheModelCantFindOnThePageIsDropped() {
        String result = figures.resolve(session, new VisionStub("{\"found\":false}"), "Text\n[[FIG:2|A dragon]]", 1, 3);

        assertThat(result).isEqualTo("Text\n");
        verify(storage, never()).put(anyString(), any(), anyString());
    }

    @Test
    void whenLocatingFailsTheWholePageIsUsed() throws IOException {
        String result = figures.resolve(session, new VisionStub(null), "[[FIG:2|A map]]", 1, 3);

        assertThat(result).matches(UUID_MARKER.formatted("A map"));
        // The whole page, scaled to the stored size.
        assertThat(storedImage().getHeight()).isBetween(999, 1000);
    }

    @Test
    void aPageOutsideTheRangeIsDroppedWithoutOpeningTheBook() {
        String result = figures.resolve(session, new VisionStub("{\"found\":false}"), "Text[[FIG:9|Nope]]", 1, 3);

        assertThat(result).isEqualTo("Text");
        verify(storage, never()).get(anyString());
    }

    @Test
    void noMoreThanTwelveFiguresAreCut() {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < 15; i++) {
            text.append("[[FIG:1|Figure ").append(i).append("]]\n");
        }

        figures.resolve(session, new VisionStub(null), text.toString(), 1, 3);

        verify(storage, times(SummaryFigures.MAX_FIGURES)).put(anyString(), any(), anyString());
    }
}
