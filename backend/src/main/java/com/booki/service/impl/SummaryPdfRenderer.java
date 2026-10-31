package com.booki.service.impl;

import com.booki.storage.StorageAdapter;
import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import lombok.RequiredArgsConstructor;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.Image;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Builds the summary PDF from the same Markdown saved in the chat: Markdown to
 * HTML with commonmark, HTML to PDF with openhtmltopdf.
 *
 * <p>The text comes from a model that read a PDF, so it is untrusted: raw HTML is
 * escaped and Markdown images are removed, which leaves nothing that could make
 * the server fetch a URL. The only images are the book's figures, inlined here.
 */
@Component
@RequiredArgsConstructor
public class SummaryPdfRenderer {

    private static final Pattern FIGURE_MARKER = Pattern.compile("\\[\\[FIG:([0-9a-f-]{36})\\|([^\\]\\n]*)]]");
    /** Characters XML doesn't allow, which would make the whole document unparseable. */
    private static final Pattern XML_INVALID = Pattern.compile("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]");

    private static final List<org.commonmark.Extension> EXTENSIONS = List.of(TablesExtension.create());
    private static final Parser PARSER = Parser.builder().extensions(EXTENSIONS).build();
    private static final HtmlRenderer RENDERER = HtmlRenderer.builder()
            .extensions(EXTENSIONS)
            .escapeHtml(true)
            .sanitizeUrls(true)
            .build();

    private static final String CSS = """
            @page { size: letter; margin: 36pt 40pt 40pt 40pt;
                    @bottom-center { content: counter(page) " / " counter(pages); font-family: 'DejaVu Sans';
                                     font-size: 7pt; color: #8a8a94; } }
            body { margin: 0; font-family: 'DejaVu Sans'; font-size: 9pt; line-height: 1.3; color: #24242c; }
            .brand { color: #e63946; font-size: 13pt; font-weight: bold; }
            .title { font-size: 9.5pt; font-weight: bold; color: #17171f; margin: 1pt 0 7pt 0;
                     padding-bottom: 4pt; border-bottom: 1.5pt solid #e63946; }
            .cover { float: right; width: 28pt; height: 28pt; line-height: 28pt; text-align: center;
                     color: #ffffff; font-size: 10pt; font-weight: bold; }
            h1, h2 { font-size: 10.5pt; color: #17171f; margin: 8pt 0 3pt 0; padding-bottom: 1.5pt;
                     border-bottom: 0.75pt solid #d9d9e0; page-break-after: avoid; }
            h3, h4 { font-size: 9.5pt; color: #e63946; margin: 5pt 0 2pt 0; page-break-after: avoid; }
            p { margin: 0 0 3pt 0; }
            ul, ol { margin: 0 0 3pt 0; padding-left: 11pt; }
            li { margin-bottom: 1pt; }
            /* A margin after the last line could spill alone onto a blank page. */
            .notes { margin: 0; }
            .notes > :last-child, .notes > :last-child > li:last-child { margin-bottom: 0; }
            strong { font-weight: bold; color: #17171f; }
            em { font-style: italic; }
            hr { border: 0; border-top: 0.75pt solid #d9d9e0; margin: 5pt 0; }
            code { font-size: 8pt; background-color: #f1f1f4; }
            blockquote { margin: 0 0 3pt 0; padding: 3pt 6pt; background-color: #f6f6f9;
                         border-left: 2pt solid #e63946; }
            table { width: 100%; border-collapse: collapse; margin: 2pt 0 5pt 0; font-size: 8pt;
                    -fs-table-paginate: paginate; }
            th { background-color: #17171f; color: #ffffff; font-weight: bold; text-align: left; padding: 1.5pt 4pt; }
            td { border: 0.75pt solid #d9d9e0; padding: 1.5pt 4pt; vertical-align: top; }
            tr { page-break-inside: avoid; }
            .figure { text-align: center; margin: 3pt 0 4pt 0; page-break-inside: avoid; }
            .figure img { max-width: 50%; max-height: 110pt; }
            .caption { font-size: 7.5pt; font-style: italic; color: #5f5f6a; margin-top: 1pt; }
            """;

    private final StorageAdapter storage;

    public record Cover(float[] color, String initials) {
    }

    public byte[] render(Long sessionId, String title, String markdown, Cover cover) {
        String html = "<html><head><meta charset=\"UTF-8\"/><title>" + escape(title) + "</title><style>"
                + CSS + "</style></head><body>" + coverHtml(cover)
                + "<div class=\"brand\">BooKI</div><div class=\"title\">" + escape(title) + "</div>"
                + "<div class=\"notes\">" + bodyHtml(sessionId, markdown) + "</div></body></html>";
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            font(builder, "DejaVuSansCondensed.ttf", 400, BaseRendererBuilder.FontStyle.NORMAL);
            font(builder, "DejaVuSansCondensed-Bold.ttf", 700, BaseRendererBuilder.FontStyle.NORMAL);
            font(builder, "DejaVuSansCondensed-Oblique.ttf", 400, BaseRendererBuilder.FontStyle.ITALIC);
            builder.withHtmlContent(XML_INVALID.matcher(html).replaceAll(""), null);
            builder.toStream(out);
            builder.run();
        } catch (Exception e) {
            throw new IllegalStateException("Could not build the summary PDF", e);
        }
        return out.toByteArray();
    }

    /** With an encoding, only markup characters are escaped: names like {@code &egrave;} aren't valid XML. */
    private static String escape(String text) {
        return HtmlUtils.htmlEscape(text, "UTF-8");
    }

    private static void font(PdfRendererBuilder builder, String file, int weight, BaseRendererBuilder.FontStyle style) {
        builder.useFont(() -> SummaryPdfRenderer.class.getResourceAsStream("/fonts/" + file),
                "DejaVu Sans", weight, style, true);
    }

    private static String coverHtml(Cover cover) {
        if (cover == null) {
            return "";
        }
        String color = "rgb(%d,%d,%d)".formatted((int) (cover.color()[0] * 255), (int) (cover.color()[1] * 255),
                (int) (cover.color()[2] * 255));
        return "<div class=\"cover\" style=\"background-color:" + color + "\">"
                + escape(cover.initials()) + "</div>";
    }

    /** Figures become placeholder paragraphs for commonmark, then real images in its output. */
    private String bodyHtml(Long sessionId, String markdown) {
        List<String> figures = new ArrayList<>();
        Matcher matcher = FIGURE_MARKER.matcher(markdown);
        String withPlaceholders = matcher.replaceAll(match -> {
            figures.add(figureHtml(sessionId, match.group(1), match.group(2)));
            return "\n\nBOOKIFIGURE" + (figures.size() - 1) + "\n\n";
        });

        Node document = PARSER.parse(withPlaceholders);
        document.accept(new AbstractVisitor() {
            @Override
            public void visit(Image image) {
                image.unlink();
            }
        });
        String html = RENDERER.render(document);
        for (int i = 0; i < figures.size(); i++) {
            html = html.replace("<p>BOOKIFIGURE" + i + "</p>", figures.get(i));
        }
        return html;
    }

    private String figureHtml(Long sessionId, String figureId, String caption) {
        byte[] jpeg;
        try (InputStream in = storage.get(SummaryFigures.storageKey(sessionId, figureId)).getInputStream()) {
            jpeg = in.readAllBytes();
        } catch (IOException | RuntimeException e) {
            return ""; // A lost image shouldn't cost the reader the whole PDF.
        }
        return "<div class=\"figure\"><img src=\"data:image/jpeg;base64,"
                + Base64.getEncoder().encodeToString(jpeg) + "\" alt=\"\"/><div class=\"caption\">"
                + escape(caption) + "</div></div>";
    }
}
