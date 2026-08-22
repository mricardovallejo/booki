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
import com.booki.service.ReportService;
import com.booki.service.SessionService;
import com.booki.util.SecurityUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

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
                                                             @RequestBody Map<String, Integer> body) {
        return ResponseEntity.ok(sessionService.updateCurrentPage(SecurityUtil.currentUserId(), id, body.get("currentPage")));
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
                                                                  @RequestBody SendReportRequest request) {
        SentReportResponse response = reportService.sendProgressReport(SecurityUtil.currentUserId(), id, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{id}/reports/quiz")
    public ResponseEntity<SentReportResponse> sendQuizReport(@PathVariable Long id,
                                                              @RequestBody SendReportRequest request) {
        SentReportResponse response = reportService.sendQuizReport(SecurityUtil.currentUserId(), id, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{id}/summary")
    public ResponseEntity<Object> generateSummary(@PathVariable Long id,
                                                   @RequestBody GenerateSummaryRequest request) {
        Object response = reportService.generateSummary(SecurityUtil.currentUserId(), id, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
