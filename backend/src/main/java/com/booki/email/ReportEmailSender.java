package com.booki.email;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

/**
 * Best-effort SMTP delivery for a sent report's PDF (progress/quiz/summary).
 * Disabled — {@link #isEnabled()} false, {@link #send} a no-op returning a
 * "not configured" {@link Result} — whenever {@code spring.mail.host} or
 * {@code booki.email.from} is blank, so a deployment without SMTP configured
 * degrades to the existing "simulated" behavior instead of crashing. A send
 * failure (bad address, provider outage) is logged WITH its stack trace
 * (previously only {@code e.toString()} — no trace, unactionable) and
 * returned as a failed {@link Result} instead of swallowed — the report is
 * already generated and downloadable either way, but callers can now surface
 * *why* delivery failed instead of a generic "simulated" message. See ADR-030.
 */
@Slf4j
@Component
public class ReportEmailSender {

    private static final String BODY = "Attached is your BooKI report.";

    private final JavaMailSender mailSender;
    private final String from;
    private final boolean enabled;

    public ReportEmailSender(JavaMailSender mailSender,
                             @Value("${booki.email.from}") String from,
                             @Value("${spring.mail.host}") String host) {
        this.mailSender = mailSender;
        this.from = from;
        this.enabled = !host.isBlank() && !from.isBlank();
        if (!enabled) {
            log.info("Email delivery disabled (set SMTP_HOST + EMAIL_FROM to enable) — "
                    + "sent reports stay downloadable but not actually emailed.");
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public record Result(boolean sent, String errorMessage) {
        static Result success() {
            return new Result(true, null);
        }

        static Result failure(String errorMessage) {
            return new Result(false, errorMessage);
        }
    }

    /** @param to one or more addresses, semicolon-separated (see {@link EmailList}) — every one gets the PDF. */
    public Result send(String to, String subject, byte[] pdf, String attachmentFilename) {
        if (!enabled) {
            return Result.failure("Email delivery is not configured for this BooKI instance.");
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(from);
            helper.setTo(EmailList.parse(to).toArray(String[]::new));
            helper.setSubject(subject);
            helper.setText(BODY);
            helper.addAttachment(attachmentFilename, new ByteArrayResource(pdf), "application/pdf");
            mailSender.send(message);
            return Result.success();
        } catch (MessagingException | MailException e) {
            log.warn("Could not email report to {}", to, e);
            return Result.failure("Could not send the email: " + e.getMessage());
        }
    }
}
