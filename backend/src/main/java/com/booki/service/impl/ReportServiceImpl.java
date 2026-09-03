package com.booki.service.impl;

import com.booki.ai.AiProvider;
import com.booki.ai.AiProviderRegistry;
import com.booki.domain.Document;
import com.booki.domain.DocumentPage;
import com.booki.domain.AiProfile;
import com.booki.domain.Message;
import com.booki.domain.QuizAttempt;
import com.booki.domain.SlotKey;
import com.booki.prompt.PromptAssembler;
import com.booki.domain.SentReport;
import com.booki.domain.Session;
import com.booki.dto.GenerateSummaryRequest;
import com.booki.dto.MessageResponse;
import com.booki.dto.SendReportRequest;
import com.booki.dto.SentReportResponse;
import com.booki.dto.SessionProgressResponse;
import com.booki.repository.DocumentPageRepository;
import com.booki.repository.DocumentRepository;
import com.booki.repository.MessageRepository;
import com.booki.repository.QuizAttemptRepository;
import com.booki.repository.SentReportRepository;
import com.booki.repository.SessionRepository;
import com.booki.service.ReportService;
import com.booki.storage.StorageAdapter;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class ReportServiceImpl implements ReportService {

    private final SessionRepository sessionRepository;
    private final DocumentRepository documentRepository;
    private final DocumentPageRepository documentPageRepository;
    private final MessageRepository messageRepository;
    private final QuizAttemptRepository quizAttemptRepository;
    private final SentReportRepository sentReportRepository;
    private final SessionProgressCalculator progressCalculator;
    private final PdfReportBuilder pdfReportBuilder;
    private final AiProviderRegistry aiProviderRegistry;
    private final PromptAssembler promptAssembler;
    private final StorageAdapter storage;

    private static final Map<String, String> LANGUAGE_NAMES = Map.of("en", "English", "es", "Spanish", "fr", "French");
    private static final Map<String, String> SUMMARY_HEADING = Map.of("en", "Summary", "es", "Resumen", "fr", "Résumé");
    private static final Pattern EMAIL_RE = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    @Override
    public List<SentReportResponse> listReports(Long userId, Long sessionId) {
        Session session = findOwned(userId, sessionId);
        return sentReportRepository.findBySessionIdOrderByCreatedAtDesc(session.getId()).stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
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
                        "Quizzes taken: " + progress.getQuizzesTaken(),
                        "Quiz average score: " + progress.getQuizAverageScore() + "%"
                ))
        );

        byte[] pdf = pdfReportBuilder.build(
                "Progress report — " + sessionTitle(session),
                "Generated " + Instant.now(),
                sections,
                null);

        String fileName = writeReportFile(pdf);
        SentReport report = saveSentReport(session, "progress", email, fileName);
        return toResponse(report);
    }

    @Override
    public SentReportResponse sendQuizReport(Long userId, Long sessionId, SendReportRequest request) {
        Session session = findOwned(userId, sessionId);
        String email = requireValidEmail(request.getEmail());

        List<QuizAttempt> attempts = quizAttemptRepository.findBySessionIdOrderByCreatedAtAsc(session.getId());
        if (attempts.isEmpty()) {
            throw new IllegalArgumentException("No quiz answers to report yet for this session");
        }

        Document document = documentRepository.findById(session.getDocument().getId()).orElse(null);
        long correctCount = attempts.stream().filter(QuizAttempt::getCorrect).count();
        int avgScore = (int) Math.round(attempts.stream().mapToDouble(QuizAttempt::getScore).average().orElse(0) * 100);

        List<PdfReportBuilder.Section> sections = new java.util.ArrayList<>();
        sections.add(new PdfReportBuilder.Section("Session", List.of(
                "Document: " + (document != null ? document.getTitle() : "Unknown"),
                "Pages: " + session.getStartPage() + "-" + session.getEndPage()
        )));
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
                "Quiz correction report — " + sessionTitle(session),
                "Generated " + Instant.now(),
                sections,
                null);

        String fileName = writeReportFile(pdf);
        SentReport report = saveSentReport(session, "quiz", email, fileName);
        return toResponse(report);
    }

    @Override
    public Object generateSummary(Long userId, Long sessionId, GenerateSummaryRequest request) {
        Session session = findOwned(userId, sessionId);
        boolean deliverAsPdf = "pdf".equals(request.getDeliverAs());

        String email = null;
        if (deliverAsPdf && request.getEmail() != null && !request.getEmail().isBlank()) {
            email = requireValidEmail(request.getEmail());
        }

        String summaryText = generateSummaryText(session, request.getLengthPages(), request.getPrompt());

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
        List<PdfReportBuilder.Section> sections = List.of(new PdfReportBuilder.Section(heading, List.of(summaryText)));

        byte[] pdf = pdfReportBuilder.build("Summary — " + sessionTitle(session), null, sections, cover);
        String fileName = writeReportFile(pdf);
        SentReport report = saveSentReport(session, "summary", email, fileName);
        return toResponse(report);
    }

    @Override
    public Resource downloadReportFile(Long userId, Long reportId) {
        SentReport report = sentReportRepository.findByIdAndSessionUserId(reportId, userId)
                .orElseThrow(() -> new NoSuchElementException("Report not found"));
        return storage.get("reports/" + report.getFileName());
    }

    /**
     * Real AI call grounded in the book pages (scaled by lengthPages) and the
     * discussion so far, on top of the session's layered prompt with the
     * {@code fn_summary} SlotPrompt.
     */
    @Override
    public String generateSummaryText(Session session, Integer lengthPages, String customPrompt) {
        int pages = Math.min(10, Math.max(1, lengthPages != null ? lengthPages : 2));
        int charsPerPage = Math.round(80 + pages * 90);
        int messageCount = Math.min(40, Math.max(2, pages * 4));

        List<DocumentPage> bookPages = documentPageRepository.findByDocumentIdAndPageNumberBetweenOrderByPageNumberAsc(
                session.getDocument().getId(), session.getStartPage(), session.getEndPage());
        String bookExcerpt = bookPages.stream()
                .map(p -> {
                    String text = p.getExtractedText();
                    boolean truncated = text.length() > charsPerPage;
                    return "p." + p.getPageNumber() + ": " + text.substring(0, Math.min(charsPerPage, text.length()))
                            + (truncated ? "…" : "");
                })
                .reduce((a, b) -> a + " " + b)
                .orElse("");

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
                session, SlotKey.FN_SUMMARY, session.getDifficulty(), contextText);

        StringBuilder instruction = new StringBuilder("Write the summary now — about " + pages
                + " page(s) long (roughly " + (pages * 250) + " words), from the excerpt and discussion above.");
        if (customPrompt != null && !customPrompt.isBlank()) {
            instruction.append(" Follow this specific request: \"").append(customPrompt.trim()).append("\".");
        }

        AiProvider provider = aiProviderRegistry.get(session.getAiProvider());
        return provider.converse(systemPrompt, List.of(), instruction.toString()).strip();
    }

    private String requireValidEmail(String email) {
        if (email == null || !EMAIL_RE.matcher(email).matches()) {
            throw new IllegalArgumentException("A valid email is required");
        }
        return email;
    }

    private String sessionTitle(Session session) {
        return session.getDocument().getTitle() + " (pages " + session.getStartPage() + "-" + session.getEndPage() + ")";
    }

    private Session findOwned(Long userId, Long sessionId) {
        return sessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new NoSuchElementException("Session not found"));
    }

    private SentReport saveSentReport(Session session, String type, String email, String fileName) {
        SentReport report = new SentReport();
        report.setSession(session);
        report.setType(type);
        report.setEmail(email);
        report.setFileName(fileName);
        return sentReportRepository.save(report);
    }

    private String writeReportFile(byte[] pdf) {
        String fileName = UUID.randomUUID() + ".pdf";
        storage.put("reports/" + fileName, pdf, "application/pdf");
        return fileName;
    }

    private SentReportResponse toResponse(SentReport report) {
        return new SentReportResponse(
                report.getId(),
                report.getSession().getId(),
                report.getType(),
                report.getEmail(),
                "/api/reports/" + report.getId() + "/file",
                report.getEmail() != null,
                report.getCreatedAt()
        );
    }
}
