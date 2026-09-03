package com.booki.service.impl;

import com.booki.domain.AiProfile;
import com.booki.domain.Session;
import com.booki.domain.SlotKey;
import com.booki.dto.SessionContextResponse;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * Assembles the system prompt for a session from the app baseline (language-aware)
 * and the session's AI Profile (persona + reader context). Shared by chat, quiz
 * and summary generation, and served via {@code GET /sessions/{id}/context}.
 *
 * <p>Stage 3 will replace this with a {@code PromptAssembler} that layers in the
 * difficulty rubric, per-function prompts and capability routing with an explicit
 * precedence. For now it keeps the previous behavior, reading from the profile.
 */
@Component
public class SessionContextBuilder {

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
        AiProfile profile = session.getAiProfile();
        return new SessionContextResponse(
                appPrompt,
                profile != null ? blankToNull(profile.text(SlotKey.PERSONA)) : null,
                profile != null ? blankToNull(profile.text(SlotKey.READER_CONTEXT)) : null);
    }

    /** Full system prompt for a session, including whatever reading material the caller passes as {@code contextText}. */
    public String buildSystemPrompt(Session session, String contextText) {
        AiProfile profile = session.getAiProfile();

        StringBuilder sb = new StringBuilder();
        sb.append(APP_PROMPT_TEMPLATE.formatted(languageName(session.getLanguage())));
        if (profile != null) {
            String persona = profile.text(SlotKey.PERSONA);
            if (!persona.isBlank()) {
                sb.append("\n\nYour persona for this session: ").append(persona);
            }
            String reader = profile.text(SlotKey.READER_CONTEXT);
            if (!reader.isBlank()) {
                sb.append("\n\nWhat this reader has told you about themselves: ").append(reader);
            }
        }
        sb.append("\n\nSession difficulty: ").append(session.getDifficulty());
        sb.append("\nDocument title: ").append(session.getDocument().getTitle());
        sb.append("\nSession page range: ").append(session.getStartPage())
                .append("-").append(session.getEndPage());
        sb.append("\nReader's current page: ").append(session.getCurrentPage());
        sb.append("\n\nDOCUMENT CONTEXT:\n").append(contextText);
        return sb.toString();
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}
