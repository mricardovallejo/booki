package com.booki.service;

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
    Resource downloadReportFile(Long userId, Long reportId);

    /**
     * The AI summary text for a session — book pages (scaled by {@code lengthPages})
     * plus the discussion so far, on the shared three-layer prompt. Shared by the
     * {@code POST /summary} endpoint and the conversational summary capability;
     * the caller decides whether to persist it, wrap it in a PDF, or both.
     */
    String generateSummaryText(Session session, Integer lengthPages, String customPrompt);
}
