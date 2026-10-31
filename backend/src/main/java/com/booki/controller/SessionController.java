package com.booki.controller;

import com.booki.dto.GenerateSummaryRequest;
import com.booki.dto.MessageRequest;
import com.booki.dto.MessageResponse;
import com.booki.dto.SendReportRequest;
import com.booki.dto.SentReportResponse;
import com.booki.dto.SessionContextResponse;
import com.booki.dto.SessionNotificationResponse;
import com.booki.dto.SessionProgressResponse;
import com.booki.dto.SessionRequest;
import com.booki.dto.SessionResponse;
import com.booki.dto.UpdateCurrentPageRequest;
import com.booki.service.ReportService;
import com.booki.service.SessionService;
import com.booki.util.SecurityUtil;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/sessions")
@RequiredArgsConstructor
public class SessionController {

    private final SessionService sessionService;
    private final ReportService reportService;

    @PostMapping
    public ResponseEntity<SessionResponse> create(@Valid @RequestBody SessionRequest request) {
        SessionResponse response = sessionService.createSession(SecurityUtil.currentUserId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<SessionResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(sessionService.getSession(SecurityUtil.currentUserId(), id));
    }

    @GetMapping("/{id}/context")
    public ResponseEntity<SessionContextResponse> getContext(@PathVariable Long id) {
        return ResponseEntity.ok(sessionService.getContext(SecurityUtil.currentUserId(), id));
    }

    @PatchMapping("/{id}/current-page")
    public ResponseEntity<SessionResponse> updateCurrentPage(@PathVariable Long id,
                                                             @Valid @RequestBody UpdateCurrentPageRequest request) {
        return ResponseEntity.ok(
                sessionService.updateCurrentPage(SecurityUtil.currentUserId(), id, request.getCurrentPage()));
    }

    @GetMapping("/{id}/messages")
    public ResponseEntity<List<MessageResponse>> getMessages(@PathVariable Long id) {
        return ResponseEntity.ok(sessionService.getMessages(SecurityUtil.currentUserId(), id));
    }

    @PostMapping("/{id}/messages")
    public ResponseEntity<MessageResponse> sendMessage(@PathVariable Long id,
                                                         @Valid @RequestBody MessageRequest request) {
        MessageResponse response = sessionService.sendMessage(SecurityUtil.currentUserId(), id, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}/progress")
    public ResponseEntity<SessionProgressResponse> getProgress(@PathVariable Long id) {
        return ResponseEntity.ok(sessionService.getProgress(SecurityUtil.currentUserId(), id));
    }

    @GetMapping("/{id}/notifications")
    public ResponseEntity<List<SessionNotificationResponse>> getNotifications(@PathVariable Long id) {
        return ResponseEntity.ok(sessionService.getNotifications(SecurityUtil.currentUserId(), id));
    }

    @GetMapping("/{id}/reports")
    public ResponseEntity<List<SentReportResponse>> listReports(@PathVariable Long id) {
        return ResponseEntity.ok(reportService.listReports(SecurityUtil.currentUserId(), id));
    }

    @PostMapping("/{id}/reports/progress")
    public ResponseEntity<SentReportResponse> sendProgressReport(@PathVariable Long id,
                                                                  @Valid @RequestBody SendReportRequest request) {
        SentReportResponse response = reportService.sendProgressReport(SecurityUtil.currentUserId(), id, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{id}/reports/quiz")
    public ResponseEntity<SentReportResponse> sendQuizReport(@PathVariable Long id,
                                                              @Valid @RequestBody SendReportRequest request) {
        SentReportResponse response = reportService.sendQuizReport(SecurityUtil.currentUserId(), id, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{id}/summary")
    public ResponseEntity<Object> generateSummary(@PathVariable Long id,
                                                   @Valid @RequestBody GenerateSummaryRequest request) {
        Object response = reportService.generateSummary(SecurityUtil.currentUserId(), id, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * The same summary, written as it is generated. Stays on the request thread
     * (no async dispatch) so the security context and log fields stay available.
     * Bad input still gets a normal JSON error: nothing is written until it passes.
     */
    @PostMapping("/{id}/summary/stream")
    public void streamSummary(@PathVariable Long id, @Valid @RequestBody GenerateSummaryRequest request,
                              HttpServletResponse response) {
        reportService.streamSummary(SecurityUtil.currentUserId(), id, request, new SseSummaryWriter(response));
    }

    @GetMapping("/{id}/figures/{figureId}")
    public ResponseEntity<Resource> getSummaryFigure(@PathVariable Long id, @PathVariable String figureId) {
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=86400")
                .body(reportService.getSummaryFigure(SecurityUtil.currentUserId(), id, figureId));
    }
}
