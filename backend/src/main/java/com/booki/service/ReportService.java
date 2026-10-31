package com.booki.service;

import com.booki.ai.ActivityContent;
import com.booki.domain.Session;
import com.booki.dto.GenerateSummaryRequest;
import com.booki.dto.SendReportRequest;
import com.booki.dto.SentReportResponse;
import org.springframework.core.io.Resource;

import java.util.List;

public interface ReportService {
    List<SentReportResponse> listReports(Long userId, Long sessionId);
    SentReportResponse sendProgressReport(Long userId, Long sessionId, SendReportRequest request);
    SentReportResponse sendQuizReport(Long userId, Long sessionId, SendReportRequest request);
    Object generateSummary(Long userId, Long sessionId, GenerateSummaryRequest request);

    /**
     * {@link #generateSummary} written as it is generated. Invalid input throws
     * before {@link SummaryStreamListener#onStart}; after that, every outcome
     * goes to the listener.
     */
    void streamSummary(Long userId, Long sessionId, GenerateSummaryRequest request, SummaryStreamListener listener);

    /** An image cut from the book for one of this session's summaries. */
    Resource getSummaryFigure(Long userId, Long sessionId, String figureId);

    Resource downloadReportFile(Long userId, Long reportId);

    /**
     * The AI summary text for a session — the resolved page content plus the
     * discussion so far, on the shared three-layer prompt. A null
     * {@code lengthPages} lets coverage decide the length. Shared by the
     * {@code POST /summary} endpoint and the conversational summary capability;
     * the caller decides whether to persist it, wrap it in a PDF, or both.
     */
    String generateSummaryText(Session session, Integer lengthPages, String customPrompt, ActivityContent content);
}
