package com.booki.service.impl;

import com.booki.ai.ActivityContent;
import com.booki.ai.ActivityContentService;
import com.booki.ai.AiProvider;
import com.booki.ai.AiProviderRegistry;
import com.booki.ai.PageRangeResolver;
import com.booki.domain.Document;
import com.booki.domain.AiProfile;
import com.booki.domain.Message;
import com.booki.domain.PageMode;
import com.booki.domain.QuizAttempt;
import com.booki.domain.QuizRequest;
import com.booki.domain.AppProfileSlotKey;
import com.booki.prompt.PromptAssembler;
import com.booki.domain.SentReport;
import com.booki.domain.Session;
import com.booki.email.EmailList;
import com.booki.email.ReportEmailSender;
import com.booki.dto.GenerateSummaryRequest;
import com.booki.dto.MessageResponse;
import com.booki.dto.SendReportRequest;
import com.booki.dto.SentReportResponse;
import com.booki.dto.SessionProgressResponse;
import com.booki.repository.DocumentRepository;
import com.booki.repository.MessageRepository;
import com.booki.repository.QuizAttemptRepository;
import com.booki.repository.QuizRequestRepository;
import com.booki.repository.SentReportRepository;
import com.booki.repository.SessionRepository;
import com.booki.service.ReportService;
import com.booki.storage.StorageAdapter;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReportServiceImpl implements ReportService {

    private final SessionRepository sessionRepository;
    private final DocumentRepository documentRepository;
    private final ActivityContentService activityContentService;
    private final MessageRepository messageRepository;
    private final QuizAttemptRepository quizAttemptRepository;
    private final QuizRequestRepository quizRequestRepository;
    private final SentReportRepository sentReportRepository;
    private final SessionProgressCalculator progressCalculator;
    private final PdfReportBuilder pdfReportBuilder;
    private final AiProviderRegistry aiProviderRegistry;
    private final PromptAssembler promptAssembler;
    private final PageRangeResolver pageRangeResolver;
    private final StorageAdapter storage;
    private final ReportEmailSender emailSender;

    private static final Map<String, String> LANGUAGE_NAMES = Map.of("en", "English", "es", "Spanish", "fr", "French");
    private static final Map<String, String> SUMMARY_HEADING = Map.of("en", "Summary", "es", "Resumen", "fr", "Résumé");

    @Override
    @Transactional(readOnly = true)
    public List<SentReportResponse> listReports(Long userId, Long sessionId) {
        Session session = findOwned(userId, sessionId);
        return sentReportRepository.findBySessionIdOrderByCreatedAtDesc(session.getId()).stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public SentReportResponse sendProgressReport(Long userId, Long sessionId, SendReportRequest request) {
        Session session = findOwned(userId, sessionId);
        String email = requireValidEmail(request.getEmail());

        Document document = documentRepository.findById(session.getDocument().getId()).orElse(null);
        AiProfile profile = session.getAiProfile();
        SessionProgressResponse progress = progressCalculator.compute(session);

        List<PdfReportBuilder.Section> sections = List.of(
                new PdfReportBuilder.Section("Session", List.of(
                        "Document: " + (document != null ? document.getTitle() : "Unknown"),
                        "Pages: " + session.getStartPage() + "-" + session.getEndPage()
                                + " · Difficulty: " + session.getDifficulty()
                                + " · Language: " + LANGUAGE_NAMES.getOrDefault(session.getLanguage(), session.getLanguage()),
                        "AI profile: " + (profile != null ? profile.getName() : "None selected")
                )),
                new PdfReportBuilder.Section("Progress", List.of(
                        "Pages read: " + progress.getPagesRead() + "/" + progress.getTotalPages() + " (" + progress.getPctRead() + "%)",
                        "Messages exchanged: " + progress.getMessageCount(),
                        "Quiz questions answered: " + progress.getQuestionsAnswered(),
                        "Quiz average score: " + progress.getQuizAverageScore() + "%"
                ))
        );

        byte[] pdf = pdfReportBuilder.build(
                "Progress report — " + sessionTitle(session),
                "Generated " + Instant.now(),
                sections,
                null);

        String fileName = writeReportFile(pdf);
        SavedReport saved = saveSentReport(session, "progress", email, pdf, fileName,
                "Progreso: " + progress.getPctRead() + "%", session.getStartPage(), session.getEndPage());
        return toResponse(saved);
    }

    @Override
    @Transactional
    public SentReportResponse sendQuizReport(Long userId, Long sessionId, SendReportRequest request) {
        Session session = findOwned(userId, sessionId);
        String email = requireValidEmail(request.getEmail());

        List<QuizAttempt> allAttempts = quizAttemptRepository.findBySessionIdOrderByCreatedAtAsc(session.getId());
        if (allAttempts.isEmpty()) {
            throw new IllegalArgumentException("No quiz answers to report yet for this session");
        }

        // Prefer the round the caller named, else the session's most recent
        // round, else (fully legacy data, no QuizRequest ever recorded) fall
        // back to the old best-effort reconstruction from attempts alone.
        QuizRequest quizRequest = request.getQuizRequestId() != null
                ? quizRequestRepository.findByIdAndSessionId(request.getQuizRequestId(), session.getId()).orElse(null)
                : null;
        if (quizRequest == null) {
            quizRequest = quizRequestRepository.findTopBySessionIdOrderByCreatedAtDesc(session.getId()).orElse(null);
        }

        Long quizRequestId = quizRequest != null ? quizRequest.getId() : null;
        List<QuizAttempt> attempts = quizRequestId != null
                ? allAttempts.stream()
                        .filter(a -> a.getQuizRequest() != null && quizRequestId.equals(a.getQuizRequest().getId()))
                        .toList()
                : allAttempts;
        if (attempts.isEmpty()) {
            // The named/most-recent round has no answers yet — report on everything instead of an empty PDF.
            attempts = allAttempts;
            quizRequest = null;
        }

        Document document = documentRepository.findById(session.getDocument().getId()).orElse(null);
        long correctCount = attempts.stream().filter(QuizAttempt::getCorrect).count();
        int avgScore = (int) Math.round(attempts.stream().mapToDouble(QuizAttempt::getScore).average().orElse(0) * 100);

        // The actual range these quiz questions covered — not the session's own
        // (auto-growing) start/end page, which can diverge from it.
        int coveredStart = attempts.stream().mapToInt(QuizAttempt::getPageNumber).min().orElse(session.getStartPage());
        int coveredEnd = attempts.stream().mapToInt(QuizAttempt::getPageNumber).max().orElse(session.getEndPage());

        List<String> sessionLines = new java.util.ArrayList<>();
        sessionLines.add("Document: " + (document != null ? document.getTitle() : "Unknown"));
        if (quizRequest != null) {
            sessionLines.add("Requested pages: " + quizRequest.getStartPage() + "-" + quizRequest.getEndPage());
            if (quizRequest.getStartPage() != coveredStart || quizRequest.getEndPage() != coveredEnd) {
                sessionLines.add("Pages actually covered: " + coveredStart + "-" + coveredEnd);
            }
            sessionLines.add("Requested questions: " + quizRequest.getQuestionCount() + " · Answered: " + attempts.size());
            sessionLines.add("Requested difficulty: " + quizRequest.getDifficulty());
        } else {
            sessionLines.add("Pages covered (requested range not recorded): " + coveredStart + "-" + coveredEnd);
            sessionLines.add("Answered: " + attempts.size());
        }

        int startPage = quizRequest != null ? quizRequest.getStartPage() : coveredStart;
        int endPage = quizRequest != null ? quizRequest.getEndPage() : coveredEnd;

        List<PdfReportBuilder.Section> sections = new java.util.ArrayList<>();
        sections.add(new PdfReportBuilder.Section("Session", sessionLines));
        sections.add(new PdfReportBuilder.Section("Quiz summary", List.of(
                attempts.size() + " answered · " + correctCount + " correct · " + avgScore + "% average score"
        )));
        for (int i = 0; i < attempts.size(); i++) {
            QuizAttempt a = attempts.get(i);
            sections.add(new PdfReportBuilder.Section(
                    "Question " + (i + 1) + " — Page " + a.getPageNumber() + " (" + a.getDifficulty() + ")",
                    List.of(
                            "Q: " + a.getQuestion(),
                            "Answer: " + a.getAnswer(),
                            "Result: " + (a.getCorrect() ? "Correct" : "Needs work"),
                            "Feedback: " + a.getFeedback()
                    )));
        }

        byte[] pdf = pdfReportBuilder.build(
                "Quiz correction report — " + sessionTitle(session, startPage, endPage),
                "Generated " + Instant.now(),
                sections,
                null);

        String fileName = writeReportFile(pdf);
        SavedReport saved = saveSentReport(session, "quiz", email, pdf, fileName,
                "Nota: " + avgScore + "%", startPage, endPage);
        return toResponse(saved);
    }

    // Not @Transactional: generateSummaryText makes a slow model call, and a
    // transaction spanning it would hold a DB connection open for its duration.
    // The only writes are a single Message row or a single SentReport row.
    @Override
    public Object generateSummary(Long userId, Long sessionId, GenerateSummaryRequest request) {
        Session session = findOwned(userId, sessionId);
        boolean deliverAsPdf = "pdf".equals(request.getDeliverAs());

        String email = null;
        if (deliverAsPdf && request.getEmail() != null && !request.getEmail().isBlank()) {
            email = requireValidEmail(request.getEmail());
        }

        PageRangeResolver.PhysicalRange range = pageRangeResolver.resolve(
                session.getDocument(), request.getStartPage(), request.getEndPage(), PageMode.ofWire(request.getPageMode()));
        int startPage = range.start();
        int endPage = range.end();
        AiProvider provider = aiProviderRegistry.get(session.getAiProvider());
        ActivityContent content = activityContentService.resolve(session.getDocument(), provider, startPage, endPage);
        String summaryText = generateSummaryText(session, request.getLengthPages(), request.getPrompt(), content);

        if (!deliverAsPdf) {
            Message botMessage = new Message();
            botMessage.setSession(session);
            botMessage.setSpeaker(Message.Speaker.BOOKI);
            botMessage.setInputType(Message.InputType.TEXT);
            botMessage.setMessage(summaryText);
            messageRepository.save(botMessage);

            MessageResponse response = new MessageResponse();
            response.setId(botMessage.getId());
            response.setSpeaker(botMessage.getSpeaker().name());
            response.setInputType(botMessage.getInputType().name());
            response.setMessage(botMessage.getMessage());
            response.setCreatedAt(botMessage.getCreatedAt());
            return response;
        }

        Document document = documentRepository.findById(session.getDocument().getId()).orElse(null);
        PdfReportBuilder.Cover cover = null;
        if (Boolean.TRUE.equals(request.getIncludeCover()) && document != null) {
            cover = new PdfReportBuilder.Cover(
                    pdfReportBuilder.coverColorFor(document.getTitle(), document.getId()),
                    pdfReportBuilder.initialsFor(document.getTitle()));
        }

        String heading = SUMMARY_HEADING.getOrDefault(
                promptAssembler.resolveLanguage(session.getLanguage()), SUMMARY_HEADING.get("en"));
        List<PdfReportBuilder.Section> sections = List.of(new PdfReportBuilder.Section(heading, List.of(
                "Pages: " + startPage + "-" + endPage,
                summaryText
        )));

        byte[] pdf = pdfReportBuilder.build("Summary — " + sessionTitle(session, startPage, endPage), null, sections, cover);
        String fileName = writeReportFile(pdf);
        SavedReport saved = saveSentReport(session, "summary", email, pdf, fileName, "Resumen", startPage, endPage);
        return toResponse(saved);
    }

    @Override
    public Resource downloadReportFile(Long userId, Long reportId) {
        SentReport report = sentReportRepository.findByIdAndSessionUserId(reportId, userId)
                .orElseThrow(() -> new NoSuchElementException("Report not found"));
        return storage.get("reports/" + report.getFileName());
    }

    /** Ceiling on the book excerpt sent with a summary, so a wide range can't blow up the request — only matters for the plain-text fallback (Kimi/Ollama); a document-capable provider reads the actual PDF instead. */
    private static final int SUMMARY_EXCERPT_CHAR_BUDGET = 12000;

    /**
     * Real AI call grounded in the resolved page content (scaled by lengthPages)
     * and the discussion so far, on top of the session's layered prompt with the
     * {@code fn_summary} App Profile slot.
     */
    @Override
    public String generateSummaryText(Session session, Integer lengthPages, String customPrompt,
                                      ActivityContent content) {
        int pages = normalizedSummaryLength(lengthPages);
        String bookExcerpt = switch (content) {
            case ActivityContent.DocumentReference doc ->
                    "(See the attached document, pages " + doc.startPage() + "-" + doc.endPage() + ".)";
            case ActivityContent.PlainText text -> truncate(text.text(), SUMMARY_EXCERPT_CHAR_BUDGET);
        };

        int messageCount = Math.min(40, Math.max(2, pages * 4));
        List<Message> sessionMessages = messageRepository.findBySessionIdOrderByCreatedAtAsc(session.getId());
        List<Message> recent = sessionMessages.subList(Math.max(0, sessionMessages.size() - messageCount), sessionMessages.size());
        String discussion = recent.isEmpty()
                ? "(none — the reader hasn't sent any chat messages yet)"
                : recent.stream()
                        .map(m -> (m.getSpeaker() == Message.Speaker.USER ? "Reader" : "BooKI") + ": "
                                + m.getMessage().substring(0, Math.min(200, m.getMessage().length())))
                        .reduce((a, b) -> a + " | " + b)
                        .orElse("");

        String contextText = "BOOK EXCERPT:\n" + bookExcerpt + "\n\nDISCUSSION SO FAR:\n" + discussion;
        String systemPrompt = promptAssembler.forFunction(
                session, AppProfileSlotKey.FN_SUMMARY, session.getDifficulty(), contextText);

        StringBuilder instruction = new StringBuilder("Write the summary now — about " + pages
                + " page(s) long (roughly " + (pages * 250) + " words), from the excerpt and discussion above.");
        if (customPrompt != null && !customPrompt.isBlank()) {
            instruction.append(" Follow this specific request: \"").append(customPrompt.trim()).append("\".");
        }

        AiProvider provider = aiProviderRegistry.get(session.getAiProvider());
        return activityContentService.converse(provider, content, systemPrompt, List.of(), instruction.toString()).strip();
    }

    private static String truncate(String text, int budget) {
        return text.length() > budget ? text.substring(0, budget) + "…" : text;
    }

    private int normalizedSummaryLength(Integer lengthPages) {
        return Math.min(10, Math.max(1, lengthPages != null ? lengthPages : 2));
    }

    /** @return the normalized, "; "-joined form of one or more addresses (see {@link EmailList}). */
    private String requireValidEmail(String email) {
        if (!EmailList.isValid(email)) {
            throw new IllegalArgumentException("A valid email (or ';'-separated list of emails) is required");
        }
        return EmailList.normalize(email);
    }

    private String sessionTitle(Session session) {
        return sessionTitle(session, session.getStartPage(), session.getEndPage());
    }

    private String sessionTitle(Session session, int startPage, int endPage) {
        return session.getDocument().getTitle() + " (pages " + startPage + "-" + endPage + ")";
    }

    private Session findOwned(Long userId, Long sessionId) {
        return sessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new NoSuchElementException("Session not found"));
    }

    /**
     * Persists the sent-report row and, if {@code email} is set, actually emails
     * the PDF — subject is "{book} — {metric} — {date} — {reader's name}" (e.g.
     * "Beekeeping 101 — Nota: 85% — 2026-09-13 — Alex"). {@code metric} is the
     * one number/label specific to this report type (grade, progress %, or a
     * plain label for a summary).
     */
    private record SavedReport(SentReport report, String emailError) {
    }

    private SavedReport saveSentReport(Session session, String type, String email, byte[] pdf, String fileName,
                                       String metric, Integer startPage, Integer endPage) {
        boolean sent = false;
        String emailError = null;
        if (email != null) {
            String subject = session.getDocument().getTitle() + " — " + metric + " — "
                    + java.time.LocalDate.now() + " — " + session.getUser().getName();
            ReportEmailSender.Result result = emailSender.send(email, subject, pdf, fileName);
            sent = result.sent();
            emailError = result.errorMessage();
        }
        SentReport report = new SentReport();
        report.setSession(session);
        report.setType(type);
        report.setEmail(email);
        report.setEmailSent(sent);
        report.setFileName(fileName);
        report.setStartPage(startPage);
        report.setEndPage(endPage);
        return new SavedReport(sentReportRepository.save(report), emailError);
    }

    private String writeReportFile(byte[] pdf) {
        String fileName = UUID.randomUUID() + ".pdf";
        storage.put("reports/" + fileName, pdf, "application/pdf");
        return fileName;
    }

    /** Historical rows carry no transient send error — only a just-sent report (see the other overload) can. */
    private SentReportResponse toResponse(SentReport report) {
        return new SentReportResponse(
                report.getId(),
                report.getSession().getId(),
                report.getType(),
                report.getEmail(),
                "/api/reports/" + report.getId() + "/file",
                report.getEmail() != null && !report.isEmailSent(),
                report.getStartPage(),
                report.getEndPage(),
                report.getCreatedAt(),
                null
        );
    }

    private SentReportResponse toResponse(SavedReport saved) {
        SentReportResponse response = toResponse(saved.report());
        response.setEmailError(saved.emailError());
        return response;
    }
}
