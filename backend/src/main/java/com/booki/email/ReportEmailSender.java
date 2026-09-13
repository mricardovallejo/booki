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
 * Disabled — {@link #isEnabled()} false, {@link #send} a no-op returning
 * {@code false} — whenever {@code spring.mail.host} or {@code booki.email.from}
 * is blank, so a deployment without SMTP configured degrades to the existing
 * "simulated" behavior instead of crashing. A send failure (bad address,
 * provider outage) is logged and swallowed the same way — the report is
 * already generated and downloadable either way.
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

    /** @return true iff the message was handed off to the SMTP server successfully. */
    public boolean send(String to, String subject, byte[] pdf, String attachmentFilename) {
        if (!enabled) {
            return false;
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(BODY);
            helper.addAttachment(attachmentFilename, new ByteArrayResource(pdf), "application/pdf");
            mailSender.send(message);
            return true;
        } catch (MessagingException | MailException e) {
            log.warn("Could not email report to {}: {}", to, e.toString());
            return false;
        }
    }
}
