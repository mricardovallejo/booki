package com.booki.service;

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
}
