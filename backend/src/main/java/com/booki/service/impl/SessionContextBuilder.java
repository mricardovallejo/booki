package com.booki.service.impl;

import com.booki.domain.Session;
import com.booki.domain.User;
import com.booki.dto.SessionContextResponse;
import com.booki.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * The three layers that shape every AI call for a session: the app's own
 * baseline behavior (language-aware), the Profile Master's persona, and the
 * user's own stated preferences. Shared by chat, quiz generation/grading,
 * and summary generation — and served directly via GET /sessions/{id}/context
 * for transparency.
 */
@Component
@RequiredArgsConstructor
public class SessionContextBuilder {

    private final UserRepository userRepository;

    private static final Set<String> SUPPORTED_LANGUAGES = Set.of("en", "es", "fr");
    private static final Map<String, String> LANGUAGE_NAMES = Map.of("en", "English", "es", "Spanish", "fr", "French");

    private static final String APP_PROMPT_TEMPLATE = """
            BooKI is a reading companion, not an authority figure. Respond in %s. \
            Keep the tone encouraging, never scold the reader for a wrong answer, \
            and always ground your answers in the session's page range below.""";

    public String resolveLanguage(String language) {
        return (language != null && SUPPORTED_LANGUAGES.contains(language)) ? language : "en";
    }

    public String languageName(String language) {
        return LANGUAGE_NAMES.getOrDefault(resolveLanguage(language), "English");
    }

    public SessionContextResponse buildContext(Session session) {
        String appPrompt = APP_PROMPT_TEMPLATE.formatted(languageName(session.getLanguage()));

        String masterPrompt = session.getProfileMaster() != null
                ? session.getProfileMaster().getSystemPrompt()
                : null;

        User user = userRepository.findById(session.getUser().getId()).orElse(null);
        String userPrompt = (user != null && user.getSystemPrompt() != null && !user.getSystemPrompt().isBlank())
                ? user.getSystemPrompt()
                : null;

        return new SessionContextResponse(appPrompt, masterPrompt, userPrompt);
    }

    /** Full system prompt for a session, including whatever reading material the caller passes as {@code contextText}. */
    public String buildSystemPrompt(Session session, String contextText) {
        SessionContextResponse context = buildContext(session);

        StringBuilder sb = new StringBuilder();
        sb.append(context.getAppPrompt());
        if (context.getMasterPrompt() != null) {
            sb.append("\n\nYour persona for this session: ").append(context.getMasterPrompt());
        }
        if (context.getUserPrompt() != null) {
            sb.append("\n\nWhat this reader has told you about themselves: ").append(context.getUserPrompt());
        }
        sb.append("\n\nSession difficulty: ").append(session.getDifficulty());
        sb.append("\nDocument title: ").append(session.getDocument().getTitle());
        sb.append("\nSession page range: ").append(session.getStartPage())
                .append("-").append(session.getEndPage());
        sb.append("\nReader's current page: ").append(session.getCurrentPage());
        sb.append("\n\nDOCUMENT CONTEXT:\n").append(contextText);
        return sb.toString();
    }
}
