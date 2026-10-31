package com.booki.controller;

import com.booki.dto.SentReportResponse;
import com.booki.service.SummaryStreamListener;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Writes a summary as Server-Sent Events on the request thread: {@code delta},
 * then one {@code done} or {@code error}. A stream that ends with neither was
 * cut by something outside BooKI (a timeout, a lost connection), and the frontend
 * tells the reader so.
 *
 * <p>If the reader closes the tab the writes start failing; that is ignored, so
 * the summary already paid for still gets saved to the chat.
 */
@RequiredArgsConstructor
class SseSummaryWriter implements SummaryStreamListener {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final HttpServletResponse response;
    private boolean clientGone;

    @Override
    public void onStart() {
        response.setContentType(MediaType.TEXT_EVENT_STREAM_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-cache");
        // Show the reader that work has started during the model's silent first seconds.
        write("start", Map.of());
    }

    @Override
    public void onDelta(String text) {
        write("delta", Map.of("text", text));
    }

    @Override
    public void onFigures() {
        write("figures", Map.of());
    }

    @Override
    public void onDone(Long messageId, boolean cutOff, SentReportResponse report, String reportError) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("messageId", messageId);
        data.put("cutOff", cutOff);
        data.put("report", report);
        data.put("reportError", reportError);
        write("done", data);
    }

    @Override
    public void onFailed(String message) {
        write("error", Map.of("error", message));
    }

    private void write(String event, Map<String, ?> data) {
        if (clientGone) {
            return;
        }
        try {
            String frame = "event: " + event + "\ndata: " + JSON.writeValueAsString(data) + "\n\n";
            response.getOutputStream().write(frame.getBytes(StandardCharsets.UTF_8));
            response.flushBuffer();
        } catch (IOException e) {
            clientGone = true;
        }
    }
}
