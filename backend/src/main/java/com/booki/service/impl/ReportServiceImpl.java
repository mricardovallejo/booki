package com.booki.service.impl;

import com.booki.domain.Document;
import com.booki.domain.DocumentPage;
import com.booki.domain.Message;
import com.booki.domain.ProfileMaster;
import com.booki.domain.QuizAttempt;
import com.booki.domain.SentReport;
import com.booki.domain.Session;
import com.booki.domain.User;
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
import com.booki.repository.UserRepository;
import com.booki.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class ReportServiceImpl implements ReportService {

    private final SessionRepository sessionRepository;
    private final DocumentRepository documentRepository;
    private final DocumentPageRepository documentPageRepository;
    private final MessageRepository messageRepository;
    private final QuizAttemptRepository quizAttemptRepository;
    private final UserRepository userRepository;
    private final SentReportRepository sentReportRepository;
    private final SessionProgressCalculator progressCalculator;
    private final PdfReportBuilder pdfReportBuilder;

    @Value("${booki.storage.report-path}")
    private String reportStoragePath;

    private static final Set<String> SUPPORTED_LANGUAGES = Set.of("en", "es", "fr");
    private static final Map<String, String> LANGUAGE_NAMES = Map.of("en", "English", "es", "Spanish", "fr", "French");
    private static final Pattern EMAIL_RE = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private record SummaryLabels(String book, String discussion, String noDiscussion) {
    }

    private static final Map<String, SummaryLabels> SUMMARY_LABELS = Map.of(
            "en", new SummaryLabels("From the book", "From our discussion", "You haven't chatted with BooKI yet in this session."),
            "es", new SummaryLabels("Del libro", "De nuestra conversación", "Todavía no has conversado con BooKI en esta sesión."),
            "fr", new SummaryLabels("Du livre", "De notre discussion", "Tu n'as pas encore discuté avec BooKI dans cette session.")
    );

    private static final Map<String, Function<String, String>> SUMMARY_INTRO = Map.of(
            "en", tone -> "Summary prepared as your " + tone + ", combining the book's content with our discussion so far.",
            "es", tone -> "Resumen preparado como tu " + tone + ", combinando el contenido del libro con nuestra conversación hasta ahora.",
            "fr", tone -> "Résumé préparé en tant que ton " + tone + ", combinant le contenu du livre et notre discussion jusqu'ici."
    );

    private static final Map<String, Function<String, String>> CUSTOM_PROMPT_NOTE = Map.of(
            "en", p -> " Following your request: \"" + p + "\".",
            "es", p -> " Siguiendo tu pedido: \"" + p + "\".",
            "fr", p -> " Suivant ta demande : \"" + p + "\"."
    );

    private static final Map<String, Function<String, String>> USER_NOTE_PREFIX = Map.of(
            "en", note -> " (Keeping in mind what you told BooKI about yourself: \"" + note + "\")",
            "es", note -> " (Recordando lo que le contaste a BooKI sobre ti: \"" + note + "\")",
            "fr", note -> " (En tenant compte de ce que tu as dit à BooKI à ton sujet : \"" + note + "\")"
    );

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
        ProfileMaster master = session.getProfileMaster();
        SessionProgressResponse progress = progressCalculator.compute(session);

        List<PdfReportBuilder.Section> sections = List.of(
                new PdfReportBuilder.Section("Session", List.of(
                        "Document: " + (document != null ? document.getTitle() : "Unknown"),
                        "Pages: " + session.getStartPage() + "-" + session.getEndPage()
                                + " · Difficulty: " + session.getDifficulty()
                                + " · Language: " + LANGUAGE_NAMES.getOrDefault(session.getLanguage(), session.getLanguage()),
                        "Profile Master: " + (master != null ? master.getName() : "None selected")
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

        SummaryContent content = buildSummaryContent(session, request.getLengthPages(), request.getPrompt());

        if (!deliverAsPdf) {
            String summaryText = content.intro + "\n\n" + content.labels.book() + ": " + content.bookPart
                    + "\n\n" + content.labels.discussion() + ": " + content.discussionPart;

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

        List<PdfReportBuilder.Section> sections = List.of(
                new PdfReportBuilder.Section(content.labels.book(), List.of(content.bookPart)),
                new PdfReportBuilder.Section(content.labels.discussion(), List.of(content.discussionPart))
        );

        byte[] pdf = pdfReportBuilder.build("Summary — " + sessionTitle(session), content.intro, sections, cover);
        String fileName = writeReportFile(pdf);
        SentReport report = saveSentReport(session, "summary", email, fileName);
        return toResponse(report);
    }

    @Override
    public Resource downloadReportFile(Long userId, Long reportId) {
        SentReport report = sentReportRepository.findByIdAndSessionUserId(reportId, userId)
                .orElseThrow(() -> new NoSuchElementException("Report not found"));
        return new FileSystemResource(Paths.get(reportStoragePath).resolve(report.getFileName()));
    }

    private record SummaryContent(String intro, String bookPart, String discussionPart, SummaryLabels labels) {
    }

    private SummaryContent buildSummaryContent(Session session, Integer lengthPages, String customPrompt) {
        String lang = resolveLanguage(session.getLanguage());
        int pages = Math.min(10, Math.max(1, lengthPages != null ? lengthPages : 2));
        int charsPerPage = Math.round(80 + pages * 90);
        int messageCount = Math.min(40, Math.max(2, pages * 4));

        SummaryLabels labels = SUMMARY_LABELS.get(lang);
        ProfileMaster master = session.getProfileMaster();
        User user = userRepository.findById(session.getUser().getId()).orElse(null);
        String tone = master != null ? master.getName() : "assistant";

        List<DocumentPage> pages_ = documentPageRepository.findByDocumentIdAndPageNumberBetweenOrderByPageNumberAsc(
                session.getDocument().getId(), session.getStartPage(), session.getEndPage());
        String bookPart = pages_.stream()
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
        String discussionPart = recent.stream()
                .map(m -> (m.getSpeaker() == Message.Speaker.USER ? "You" : "BooKI") + ": "
                        + m.getMessage().substring(0, Math.min(200, m.getMessage().length())))
                .reduce((a, b) -> a + " | " + b)
                .orElse(labels.noDiscussion());

        StringBuilder intro = new StringBuilder(SUMMARY_INTRO.get(lang).apply(tone));
        if (customPrompt != null && !customPrompt.isBlank()) {
            intro.append(CUSTOM_PROMPT_NOTE.get(lang).apply(customPrompt.trim()));
        }
        if (user != null && user.getSystemPrompt() != null && !user.getSystemPrompt().isBlank()) {
            intro.append(USER_NOTE_PREFIX.get(lang).apply(user.getSystemPrompt()));
        }

        return new SummaryContent(intro.toString(), bookPart, discussionPart, labels);
    }

    private String requireValidEmail(String email) {
        if (email == null || !EMAIL_RE.matcher(email).matches()) {
            throw new IllegalArgumentException("A valid email is required");
        }
        return email;
    }

    private String resolveLanguage(String language) {
        return SUPPORTED_LANGUAGES.contains(language) ? language : "en";
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
        try {
            Path dir = Paths.get(reportStoragePath);
            Files.createDirectories(dir);
            String fileName = UUID.randomUUID() + ".pdf";
            Files.write(dir.resolve(fileName), pdf);
            return fileName;
        } catch (IOException e) {
            throw new RuntimeException("Failed to store generated report", e);
        }
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
