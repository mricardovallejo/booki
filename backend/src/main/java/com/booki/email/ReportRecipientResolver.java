package com.booki.email;

import com.booki.domain.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * The default recipient for a user's sent reports — their own
 * {@code defaultReportEmail} if they've set one, otherwise the app-wide
 * fallback. Either may be unset, in which case there is no default and the
 * sender must type an address. See ADR-030.
 */
@Component
public class ReportRecipientResolver {

    private final String globalDefault;

    public ReportRecipientResolver(@Value("${booki.email.default-report-recipient}") String globalDefault) {
        this.globalDefault = globalDefault;
    }

    public String resolveDefault(User user) {
        if (user.getDefaultReportEmail() != null && !user.getDefaultReportEmail().isBlank()) {
            return user.getDefaultReportEmail();
        }
        return globalDefault.isBlank() ? null : globalDefault;
    }
}
