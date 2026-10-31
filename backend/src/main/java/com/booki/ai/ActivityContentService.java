package com.booki.ai;

import com.booki.config.OutboundHttp;
import com.booki.domain.Document;
import com.booki.storage.StorageAdapter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The one place that decides, per document + provider, what an activity
 * (quiz, summary, chat) actually sends the model for a page range. A
 * document-capable provider (Claude, OpenAI) gets a real PDF containing only
 * that range — physically cut from the original, never the whole book, so
 * cost/latency scale with the range size, not the book size; anything else
 * (Kimi, Ollama) falls back to plain text extracted fresh from the same
 * range. Neither path caches anything: slicing/extracting is cheap and local,
 * and re-cutting on every turn is what keeps a stale reference from ever being
 * a concern.
 *
 * <p>Every activity's model call passes through {@link #converse}, so that is
 * where each call is logged ({@code ai.call}), whichever feature made it.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ActivityContentService {

    private final StorageAdapter storage;

    public ActivityContent resolve(Document document, AiProvider provider, int startPage, int endPage) {
        byte[] fullPdf = readBytes(storage.get(document.getFilePath()));
        if (provider.supportsDocuments()) {
            byte[] slice = PdfImageShrinker.shrink(slicePdf(fullPdf, startPage, endPage));
            return new ActivityContent.DocumentReference(slice, startPage, endPage);
        }
        return new ActivityContent.PlainText(extractPlainText(fullPdf, startPage, endPage));
    }

    /** Dispatches to whichever {@link AiProvider} call fits the resolved content — the one place callers need. */
    public String converse(AiProvider provider, ActivityContent content, String systemPrompt,
                           List<AiProvider.Message> history, String userMessage) {
        String input = describe(content);
        long startedAt = System.currentTimeMillis();
        try {
            String reply = call(provider, content, systemPrompt, history, userMessage);
            log.info("ai.call ok provider={} model={} input={} durationMs={}",
                    provider.key(), provider.model(), input, System.currentTimeMillis() - startedAt);
            return reply;
        } catch (RuntimeException e) {
            log.error("ai.call failed provider={} model={} input={} durationMs={} cause={}",
                    provider.key(), provider.model(), input, System.currentTimeMillis() - startedAt,
                    OutboundHttp.describeFailure(e));
            throw e;
        }
    }

    /**
     * For a long reply, written as it is generated. A provider that can't stream
     * delivers its whole (blocking, shorter) reply as one delta; a cut-off reply
     * still ends with {@link AiProvider#CUT_OFF_NOTICE}. Never throws: {@code out}
     * always ends in exactly one of onComplete or onError.
     */
    public void converseLong(AiProvider provider, ActivityContent content, String systemPrompt,
                             List<AiProvider.Message> history, String userMessage,
                             StreamingAiProvider.TokenStream out) {
        long startedAt = System.currentTimeMillis();
        StreamingAiProvider.TokenStream logged = new StreamingAiProvider.TokenStream() {
            @Override
            public void onDelta(String text) {
                out.onDelta(text);
            }

            @Override
            public void onComplete(String fullText) {
                log.info("ai.call ok provider={} model={} input={} durationMs={} outputChars={} cutOff={}",
                        provider.key(), provider.model(), describe(content), System.currentTimeMillis() - startedAt,
                        fullText.length(), AiProvider.wasCutOff(fullText));
                out.onComplete(fullText);
            }

            @Override
            public void onError(RuntimeException error) {
                log.error("ai.call failed provider={} model={} input={} durationMs={} cause={}",
                        provider.key(), provider.model(), describe(content), System.currentTimeMillis() - startedAt,
                        OutboundHttp.describeFailure(error));
                out.onError(error);
            }
        };

        if (provider instanceof StreamingAiProvider streaming) {
            streaming.converseLongStream(systemPrompt, history, userMessage, content, logged);
            return;
        }
        String reply;
        try {
            reply = call(provider, content, systemPrompt, history, userMessage);
        } catch (RuntimeException e) {
            logged.onError(e);
            return;
        }
        logged.onDelta(reply);
        logged.onComplete(reply);
    }

    /**
     * The same long reply, for a caller that only needs the finished text. It still
     * streams from the provider: a blocking call has too little room, because models
     * that think first can spend nearly all of it before writing.
     */
    public String converseLong(AiProvider provider, ActivityContent content, String systemPrompt,
                               List<AiProvider.Message> history, String userMessage) {
        AtomicReference<String> reply = new AtomicReference<>();
        AtomicReference<RuntimeException> failure = new AtomicReference<>();
        converseLong(provider, content, systemPrompt, history, userMessage, new StreamingAiProvider.TokenStream() {
            @Override
            public void onDelta(String text) {
            }

            @Override
            public void onComplete(String fullText) {
                reply.set(fullText);
            }

            @Override
            public void onError(RuntimeException error) {
                failure.set(error);
            }
        });
        if (failure.get() != null) {
            throw failure.get();
        }
        return reply.get();
    }

    /** What to embed in the prompt's "document" section — the real text, or a note pointing at the attached pages. */
    public String documentTextFor(ActivityContent content) {
        return switch (content) {
            case ActivityContent.DocumentReference doc ->
                    "(See the attached document, pages " + doc.startPage() + "-" + doc.endPage() + ".)";
            case ActivityContent.PlainText text -> text.text();
        };
    }

    private static String call(AiProvider provider, ActivityContent content, String systemPrompt,
                               List<AiProvider.Message> history, String userMessage) {
        return switch (content) {
            case ActivityContent.DocumentReference doc -> provider.converseWithDocument(
                    systemPrompt, history, userMessage, doc.pdfBytes(), doc.startPage(), doc.endPage());
            case ActivityContent.PlainText ignored -> provider.converse(systemPrompt, history, userMessage);
        };
    }

    private static String describe(ActivityContent content) {
        return switch (content) {
            case ActivityContent.DocumentReference doc ->
                    "document pages=" + doc.startPage() + "-" + doc.endPage() + " pdfBytes=" + doc.pdfBytes().length;
            case ActivityContent.PlainText text -> "text chars=" + text.text().length();
        };
    }

    /** Physically cuts just startPage..endPage into a new, small PDF — the pages this turn actually needs, nothing else. */
    private byte[] slicePdf(byte[] fullPdfBytes, int startPage, int endPage) {
        try (PDDocument source = Loader.loadPDF(fullPdfBytes)) {
            int end = Math.min(endPage, source.getNumberOfPages());
            try (PDDocument sliced = new PDDocument()) {
                for (int i = startPage; i <= end; i++) {
                    sliced.importPage(source.getPage(i - 1));
                }
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                sliced.save(out);
                return out.toByteArray();
            }
        } catch (IOException e) {
            throw new IllegalStateException("Could not slice PDF pages " + startPage + "-" + endPage, e);
        }
    }

    private String extractPlainText(byte[] pdfBytes, int startPage, int endPage) {
        try (PDDocument pdDocument = Loader.loadPDF(pdfBytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setStartPage(startPage);
            stripper.setEndPage(Math.min(endPage, pdDocument.getNumberOfPages()));
            return stripper.getText(pdDocument).trim();
        } catch (IOException e) {
            throw new IllegalStateException("Could not extract text for pages " + startPage + "-" + endPage, e);
        }
    }

    private static byte[] readBytes(Resource resource) {
        try (InputStream in = resource.getInputStream()) {
            return in.readAllBytes();
        } catch (IOException e) {
            throw new IllegalStateException("Could not read stored PDF", e);
        }
    }
}
