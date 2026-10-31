package com.booki.email;

import com.booki.config.OutboundHttp;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

/**
 * Sends the password-reset link, in the background: a request for an existing
 * account must not take visibly longer than one for an unknown email, or the
 * response time would reveal who has an account. The link holds a live token,
 * so it is never logged.
 */
@Slf4j
@Component
public class PasswordResetEmailSender {

    private final JavaMailSender mailSender;
    private final String from;
    private final String appUrl;
    private final boolean enabled;

    public PasswordResetEmailSender(JavaMailSender mailSender,
                                    @Value("${booki.email.from}") String from,
                                    @Value("${spring.mail.host}") String host,
                                    @Value("${booki.app-url}") String appUrl) {
        this.mailSender = mailSender;
        this.from = from;
        this.appUrl = appUrl.replaceAll("/+$", "");
        this.enabled = !host.isBlank() && !from.isBlank();
    }

    @Async("backgroundTasks")
    public void send(String recipient, String accountEmail, String token) {
        if (!enabled) {
            log.warn("email.send skipped kind=password-reset reason=not-configured");
            return;
        }
        long startedAt = System.currentTimeMillis();
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
            helper.setFrom(from);
            helper.setTo(recipient);
            helper.setSubject("Reset your BooKI password");
            helper.setText("Someone asked to reset the password of the BooKI account " + accountEmail + ".\n\n"
                    + "Choose a new one here:\n\n"
                    + appUrl + "/reset-password?token=" + token + "\n\n"
                    + "The link works once and expires in 1 hour. "
                    + "If it wasn't you, ignore this email: the password stays the same.");
            mailSender.send(message);
            log.info("email.send ok kind=password-reset durationMs={}", System.currentTimeMillis() - startedAt);
        } catch (MessagingException | MailException e) {
            log.error("email.send failed kind=password-reset durationMs={} cause={}",
                    System.currentTimeMillis() - startedAt, OutboundHttp.describeFailure(e), e);
        }
    }
}
