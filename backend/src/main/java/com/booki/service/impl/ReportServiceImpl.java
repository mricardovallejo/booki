package com.booki.service.impl;

import com.booki.ai.ActivityContent;
import com.booki.ai.ActivityContentService;
import com.booki.ai.AiProvider;
import com.booki.ai.AiProviderRegistry;
import com.booki.ai.PageRangeResolver;
import com.booki.ai.StreamingAiProvider;
import com.booki.config.OutboundHttp;
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
import com.booki.service.SummaryStreamListener;
import com.booki.storage.StorageAdapter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

@Slf4j
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
    private final SummaryFigures summaryFigures;
    private final SummaryPdfRenderer summaryPdfRenderer;

    private static final Map<String, String> LANGUAGE_NAMES = Map.of("en", "English", "es", "Spanish", "fr", "French");
    private static final Map<String, String> SUMMARY_HEADING = Map.of("en", "Summary", "es", "Resumen", "fr", "Résumé");

    private static final int MAX_SUMMARY_PAGES = 50;
    /** Figures take far more room than notes, so a short range gets only a few. */
    private static final int PAGES_PER_FIGURE = 3;
    /** Anthropic rejects requests over 32 MB, and base64 adds a third. */
    private static final int MAX_SUMMARY_PDF_BYTES = 23 * 1024 * 1024;
    private static final int SUMMARY_DISCUSSION_MESSAGES = 20;

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
    // The writes are a single Message row and, for a PDF, a single SentReport row.
    @Override
    public Object generateSummary(Long userId, Long sessionId, GenerateSummaryRequest request) {
        Session session = findOwned(userId, sessionId);
        SummaryJob job = prepareSummary(session, request);
        SummaryPrompt prompt = buildSummaryPrompt(session, request.getLengthPages(), request.getPrompt(), job.content(), true);
        String reply = activityContentService.converse(job.provider(), job.content(), prompt.system(), List.of(), prompt.instruction());
        String summaryText = summaryFigures.resolve(session, job.provider(), summaryHeading(session, job) + reply.strip(),
                job.startPage(), job.endPage());
        Message botMessage = saveSummaryMessage(session, summaryText);

        if (!"pdf".equals(request.getDeliverAs())) {
            MessageResponse response = new MessageResponse();
            response.setId(botMessage.getId());
            response.setSpeaker(botMessage.getSpeaker().name());
            response.setInputType(botMessage.getInputType().name());
            response.setMessage(botMessage.getMessage());
            response.setCreatedAt(botMessage.getCreatedAt());
            return response;
        }
        return toResponse(buildSummaryReport(session, request, job, summaryText));
    }

    @Override
    public void streamSummary(Long userId, Long sessionId, GenerateSummaryRequest request,
                              SummaryStreamListener listener) {
        Session session = findOwned(userId, sessionId);
        SummaryJob job = prepareSummary(session, request);
        SummaryPrompt prompt = buildSummaryPrompt(session, request.getLengthPages(), request.getPrompt(), job.content(), true);
        String heading = summaryHeading(session, job);

        listener.onStart();
        listener.onDelta(heading);
        StringBuilder written = new StringBuilder(heading);

        activityContentService.converseLong(job.provider(), job.content(), prompt.system(), List.of(), prompt.instruction(),
                new StreamingAiProvider.TokenStream() {
                    @Override
                    public void onDelta(String text) {
                        written.append(text);
                        listener.onDelta(text);
                    }

                    @Override
                    public void onComplete(String fullText) {
                        finishSummary(session, request, job, written.toString(), listener);
                    }

                    @Override
                    public void onError(RuntimeException error) {
                        if (written.length() == heading.length()) {
                            listener.onFailed(summaryFailureMessage(error));
                            return;
                        }
                        // Part of it arrived. Keep it, clearly marked, rather than lose what was paid for.
                        onDelta(AiProvider.CUT_OFF_NOTICE);
                        finishSummary(session, request, job, written.toString(), listener);
                    }
                });
    }

    private void finishSummary(Session session, GenerateSummaryRequest request, SummaryJob job, String streamed,
                               SummaryStreamListener listener) {
        String text;
        Message saved;
        try {
            listener.onFigures();
            text = summaryFigures.resolve(session, job.provider(), streamed, job.startPage(), job.endPage());
            saved = saveSummaryMessage(session, text);
        } catch (RuntimeException e) {
            log.error("summary.save failed sessionId={}", session.getId(), e);
            listener.onFailed("The summary could not be saved. Please try again.");
            return;
        }

        SentReportResponse report = null;
        String reportError = null;
        if ("pdf".equals(request.getDeliverAs())) {
            try {
                report = toResponse(buildSummaryReport(session, request, job, text));
            } catch (RuntimeException e) {
                log.error("summary.pdf failed sessionId={}", session.getId(), e);
                reportError = "The summary is in the chat, but its PDF could not be created.";
            }
        }
        listener.onDone(saved.getId(), AiProvider.wasCutOff(text), report, reportError);
    }

    private static String summaryFailureMessage(RuntimeException error) {
        return OutboundHttp.describeFailure(error).startsWith("status=429")
                ? "These pages are too much for the AI account's current limit. Try again in a minute, or choose fewer pages."
                : "The reading assistant is temporarily unavailable. Please try again in a moment.";
    }

    /** Everything a summary needs, validated up front, before any model call or any byte of a stream. */
    private record SummaryJob(int startPage, int endPage, AiProvider provider, ActivityContent content, String email) {
    }

    private SummaryJob prepareSummary(Session session, GenerateSummaryRequest request) {
        String email = null;
        if ("pdf".equals(request.getDeliverAs()) && request.getEmail() != null && !request.getEmail().isBlank()) {
            email = requireValidEmail(request.getEmail());
        }

        PageRangeResolver.PhysicalRange range = pageRangeResolver.resolve(
                session.getDocument(), request.getStartPage(), request.getEndPage(), PageMode.ofWire(request.getPageMode()));
        if (range.end() - range.start() + 1 > MAX_SUMMARY_PAGES) {
            throw new IllegalArgumentException("A summary can cover up to " + MAX_SUMMARY_PAGES + " pages at a time.");
        }

        AiProvider provider = aiProviderRegistry.get(session.getAiProvider());
        ActivityContent content = activityContentService.resolve(session.getDocument(), provider, range.start(), range.end());
        if (content instanceof ActivityContent.DocumentReference doc && doc.pdfBytes().length > MAX_SUMMARY_PDF_BYTES) {
            throw new IllegalArgumentException("These pages are too large to read at once ("
                    + doc.pdfBytes().length / (1024 * 1024) + " MB). Please choose fewer pages.");
        }
        return new SummaryJob(range.start(), range.end(), provider, content, email);
    }

    /** Opens the saved text, so later chat turns know which pages this summary covers. */
    private String summaryHeading(Session session, SummaryJob job) {
        String heading = SUMMARY_HEADING.getOrDefault(
                promptAssembler.resolveLanguage(session.getLanguage()), SUMMARY_HEADING.get("en"));
        return "## " + heading + " (" + job.startPage() + "–" + job.endPage() + ")\n\n";
    }

    /** Always a chat message, PDF or not: this is what lets the reader keep discussing the summary. */
    private Message saveSummaryMessage(Session session, String text) {
        Message message = new Message();
        message.setSession(session);
        message.setSpeaker(Message.Speaker.BOOKI);
        message.setInputType(Message.InputType.TEXT);
        message.setMessage(text);
        return messageRepository.save(message);
    }

    private SavedReport buildSummaryReport(Session session, GenerateSummaryRequest request, SummaryJob job, String text) {
        Document document = documentRepository.findById(session.getDocument().getId()).orElse(null);
        SummaryPdfRenderer.Cover cover = null;
        if (Boolean.TRUE.equals(request.getIncludeCover()) && document != null) {
            cover = new SummaryPdfRenderer.Cover(
                    pdfReportBuilder.coverColorFor(document.getTitle(), document.getId()),
                    pdfReportBuilder.initialsFor(document.getTitle()));
        }

        byte[] pdf = summaryPdfRenderer.render(session.getId(), sessionTitle(session, job.startPage(), job.endPage()), text, cover);
        String fileName = writeReportFile(pdf);
        return saveSentReport(session, "summary", job.email(), pdf, fileName, "Resumen", job.startPage(), job.endPage());
    }

    @Override
    public Resource getSummaryFigure(Long userId, Long sessionId, String figureId) {
        Session session = findOwned(userId, sessionId);
        // Parsing also keeps anything but a plain id out of the storage key.
        return storage.get(SummaryFigures.storageKey(session.getId(), UUID.fromString(figureId).toString()));
    }

    @Override
    public Resource downloadReportFile(Long userId, Long reportId) {
        SentReport report = sentReportRepository.findByIdAndSessionUserId(reportId, userId)
                .orElseThrow(() -> new NoSuchElementException("Report not found"));
        return storage.get("reports/" + report.getFileName());
    }

    /**
     * Real AI call grounded in the resolved page content and the discussion so
     * far, on top of the session's layered prompt with the {@code fn_summary}
     * App Profile slot.
     */
    @Override
    public String generateSummaryText(Session session, Integer lengthPages, String customPrompt,
                                      ActivityContent content) {
        SummaryPrompt prompt = buildSummaryPrompt(session, lengthPages, customPrompt, content, false);
        AiProvider provider = aiProviderRegistry.get(session.getAiProvider());
        return activityContentService.converse(provider, content, prompt.system(), List.of(), prompt.instruction()).strip();
    }

    private record SummaryPrompt(String system, String instruction) {
    }

    private SummaryPrompt buildSummaryPrompt(Session session, Integer lengthPages, String customPrompt,
                                             ActivityContent content, boolean withFigures) {
        // The page limit bounds this, so the text is never trimmed: dropping part
        // of it would silently produce a summary of only some of the pages.
        String bookExcerpt = activityContentService.documentTextFor(content);

        int messageCount = lengthPages == null ? SUMMARY_DISCUSSION_MESSAGES : Math.min(40, Math.max(2, lengthPages * 4));
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

        StringBuilder instruction = new StringBuilder("Write the summary now, from the excerpt and discussion above.");
        if (lengthPages != null) {
            int pages = Math.min(10, Math.max(1, lengthPages));
            instruction.append(" Make it about ").append(pages).append(" page(s) long (roughly ")
                    .append(pages * 250).append(" words).");
        }
        if (customPrompt != null && !customPrompt.isBlank()) {
            instruction.append(" Follow this specific request: \"").append(customPrompt.trim()).append("\".");
        }
        // Here rather than in the editable prompt, because SummaryFigures parses this exact format.
        if (withFigures && content instanceof ActivityContent.DocumentReference document) {
            int pages = document.endPage() - document.startPage() + 1;
            int maxFigures = Math.min(SummaryFigures.MAX_FIGURES, Math.max(2, pages / PAGES_PER_FIGURE));
            instruction.append(" Where a figure on these pages (illustration, portrait, map, timeline, diagram) shows ")
                    .append("something the notes can't say as well in words, insert on its own line, right after that ")
                    .append("point: [[FIG:<page>|<caption>]], where <page> is the page it is on, numbered as given for ")
                    .append("the attached document, and <caption> says in a few words, in the session language, what it shows. Use at most ")
                    .append(maxFigures)
                    .append(" and only real figures from the pages, never decoration.");
        }
        return new SummaryPrompt(systemPrompt, instruction.toString());
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
