package com.booki.it;

import com.booki.dto.AuthResponse;
import com.booki.dto.ForgotPasswordRequest;
import com.booki.dto.ResetPasswordRequest;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.test.context.TestPropertySource;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/** The whole flow over HTTP, with only the SMTP call replaced by one that keeps the message. */
@TestPropertySource(properties = {
        "spring.mail.host=smtp.test.invalid",
        "booki.email.from=noreply@example.com",
        "booki.app-url=https://app.example.com/",
        "booki.auth.password-reset.allowed-recipients=owner@example.com; second@example.com"
})
class PasswordResetIT extends IntegrationTestBase {

    private static final Pattern LINK = Pattern.compile("https://app\\.example\\.com/reset-password\\?token=(\\S+)");

    @Autowired
    private CapturingMailSender mail;

    @BeforeEach
    void clearMail() {
        mail.sent.clear();
    }

    @Test
    void linkGoesToTheAccountEmailAndSetsANewPassword() throws Exception {
        String email = uniqueEmail("reset");
        register(email);

        assertThat(forgot(email, null).getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

        MimeMessage message = awaitMail(1).getFirst();
        assertThat(message.getAllRecipients()[0].toString()).isEqualTo(email);
        assertThat(reset(tokenIn(message), "new-password-456").getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        assertThat(login(email, "new-password-456").getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(login(email, "password123", errorType()).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void linkCanGoToAnAllowlistedAddress() throws Exception {
        String email = uniqueEmail("allowed");
        register(email);

        forgot(email, "Owner@Example.com");

        MimeMessage message = awaitMail(1).getFirst();
        assertThat(message.getAllRecipients()[0].toString()).isEqualTo("owner@example.com");
        assertThat(message.getContent().toString()).contains(email);
    }

    @Test
    void nothingIsSentToAnAddressOutsideTheAllowlistOrForAnUnknownAccount() throws Exception {
        String victim = uniqueEmail("victim");
        register(victim);

        assertThat(forgot(victim, "attacker@example.com").getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(forgot(uniqueEmail("nobody"), null).getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

        // The pool runs tasks in order, so once this later email is out the earlier
        // requests would have sent theirs too, had they queued any.
        String other = uniqueEmail("other");
        register(other);
        forgot(other, null);

        List<MimeMessage> sent = awaitMail(1);
        assertThat(sent).hasSize(1);
        assertThat(sent.getFirst().getAllRecipients()[0].toString()).isEqualTo(other);
    }

    @Test
    void aLinkWorksOnlyOnce() throws Exception {
        String email = uniqueEmail("once");
        register(email);
        forgot(email, null);
        String token = tokenIn(awaitMail(1).getFirst());

        assertThat(reset(token, "new-password-456").getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(reset(token, "another-password-789").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(login(email, "new-password-456").getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void aResetTokenIsNotASessionAndASessionIsNotAResetToken() throws Exception {
        String email = uniqueEmail("kinds");
        AuthData user = register(email);
        forgot(email, null);
        String resetToken = tokenIn(awaitMail(1).getFirst());

        ResponseEntity<Map<String, String>> asSession = rest.exchange("/api/users/me", HttpMethod.GET,
                new HttpEntity<>(auth(resetToken)), errorType());
        assertThat(asSession.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        assertThat(reset(user.token(), "new-password-456").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        ResponseEntity<AuthResponse> stillOld = login(email, "password123");
        assertThat(stillOld.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void aGarbageTokenIsRejected() {
        assertThat(reset("not-a-token", "new-password-456").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private ResponseEntity<Void> forgot(String email, String deliverTo) {
        ForgotPasswordRequest request = new ForgotPasswordRequest();
        request.setEmail(email);
        request.setDeliverTo(deliverTo);
        return rest.postForEntity("/api/auth/forgot-password", request, Void.class);
    }

    private ResponseEntity<Map<String, String>> reset(String token, String newPassword) {
        ResetPasswordRequest request = new ResetPasswordRequest();
        request.setToken(token);
        request.setNewPassword(newPassword);
        return rest.exchange("/api/auth/reset-password", HttpMethod.POST, new HttpEntity<>(request), errorType());
    }

    private String tokenIn(MimeMessage message) throws MessagingException, IOException {
        Matcher matcher = LINK.matcher(message.getContent().toString());
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }

    private List<MimeMessage> awaitMail(int count) throws InterruptedException {
        for (int i = 0; i < 100 && mail.sent.size() < count; i++) {
            Thread.sleep(50);
        }
        assertThat(mail.sent).hasSizeGreaterThanOrEqualTo(count);
        return mail.sent;
    }

    private ParameterizedTypeReference<Map<String, String>> errorType() {
        return new ParameterizedTypeReference<>() {
        };
    }

    static class CapturingMailSender extends JavaMailSenderImpl {
        final List<MimeMessage> sent = new CopyOnWriteArrayList<>();

        @Override
        protected void doSend(MimeMessage[] mimeMessages, Object[] originalMessages) {
            sent.addAll(List.of(mimeMessages));
        }
    }

    @TestConfiguration
    static class CapturingMail {
        @Bean
        @Primary
        CapturingMailSender capturingMailSender() {
            return new CapturingMailSender();
        }
    }
}
