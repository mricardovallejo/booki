package com.booki.prompt;

import com.booki.domain.AiProfile;
import com.booki.domain.Capability;
import com.booki.domain.Session;
import com.booki.domain.SlotKey;
import com.booki.dto.SessionContextResponse;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Builds the system prompt for every AI call in a session by layering, in
 * precedence order: BooKI core, the difficulty rubric for the active level, the
 * function's locked frame + editable body (capability calls only), the persona,
 * the reader context, then the session facts and the reading text. Also produces
 * the {@link SessionContextResponse} breakdown for {@code GET /sessions/{id}/context}.
 *
 * <p>Capability routing is appended by {@code ConversationEngine} (it owns the
 * registry); this class stays capability-agnostic apart from reading the
 * profile's enabled set.
 */
@Component
public class PromptAssembler {

    private static final Set<String> SUPPORTED_LANGUAGES = Set.of("en", "es", "fr");
    private static final Map<String, String> LANGUAGE_NAMES =
            Map.of("en", "English", "es", "Spanish", "fr", "French");

    private static final Map<String, SlotKey> RUBRIC = Map.of(
            "easy", SlotKey.RUBRIC_EASY, "medium", SlotKey.RUBRIC_MEDIUM, "hard", SlotKey.RUBRIC_HARD);
    private static final Map<String, String> LEVEL_LABEL = Map.of(
            "easy", "Easy", "medium", "Medium", "hard", "Advanced");

    private static final List<SlotKey> FUNCTION_SLOTS = List.of(
            SlotKey.FN_QUIZ_QUESTION, SlotKey.FN_ANSWER_GRADING, SlotKey.FN_SUMMARY,
            SlotKey.FN_EXPLAIN, SlotKey.FN_MNEMONIC);
    private static final Map<SlotKey, String> FUNCTION_LAYER_LABEL = Map.of(
            SlotKey.FN_QUIZ_QUESTION, "When you ask for a quiz question",
            SlotKey.FN_ANSWER_GRADING, "When BooKI grades a quiz answer",
            SlotKey.FN_SUMMARY, "When you ask for a summary",
            SlotKey.FN_EXPLAIN, "When you ask BooKI to explain a passage",
            SlotKey.FN_MNEMONIC, "When you ask for a memory aid");

    // ---- small helpers reused by the services -------------------------------

    public String resolveLanguage(String language) {
        return (language != null && SUPPORTED_LANGUAGES.contains(language)) ? language : "en";
    }

    public String languageName(String language) {
        return LANGUAGE_NAMES.getOrDefault(resolveLanguage(language), "English");
    }

    public String resolveDifficulty(String difficulty) {
        return RUBRIC.containsKey(difficulty) ? difficulty : "medium";
    }

    public Set<Capability> enabledCapabilities(Session session) {
        return session.getAiProfile() != null
                ? session.getAiProfile().getEnabledCapabilities()
                : EnumSet.allOf(Capability.class);
    }

    // ---- system prompts ----------------------------------------------------

    /** System prompt for a normal chat turn (routing is appended by the caller). */
    public String forChat(Session session, String documentText) {
        return assemble(session, resolveDifficulty(session.getDifficulty()), null, documentText);
    }

    /** System prompt for a capability run — the function's frame + body are layered in. */
    public String forFunction(Session session, SlotKey functionSlot, String difficulty, String documentText) {
        return assemble(session, resolveDifficulty(difficulty), functionSlot, documentText);
    }

    private String assemble(Session session, String difficulty, SlotKey functionSlot, String documentText) {
        AiProfile profile = session.getAiProfile();
        StringBuilder sb = new StringBuilder(SlotPromptCatalog.CORE_PROMPT);

        appendSection(sb, "Difficulty", text(profile, RUBRIC.get(difficulty)));
        if (functionSlot != null) {
            appendSection(sb, "What to do this turn", framed(functionSlot, profile));
        }
        appendSection(sb, "Persona", text(profile, SlotKey.PERSONA));
        appendSection(sb, "Reader", text(profile, SlotKey.READER_CONTEXT));
        appendSection(sb, "This session", sessionFacts(session));

        sb.append("\n\nDOCUMENT CONTEXT:\n").append(documentText);
        return sb.toString();
    }

    // ---- context breakdown -----------------------------------------------

    public SessionContextResponse describe(Session session) {
        AiProfile profile = session.getAiProfile();
        String language = resolveLanguage(session.getLanguage());
        String difficulty = resolveDifficulty(session.getDifficulty());
        String source = profile != null ? "AI profile \"" + profile.getName() + "\"" : "AI profile";
        Set<Capability> enabled = enabledCapabilities(session);

        List<SessionContextResponse.Layer> layers = new ArrayList<>();
        layers.add(layer("core", "core", "BooKI core", false, "App", SlotPromptCatalog.CORE_PROMPT));
        layers.add(layer("rubric", "difficulty", "Difficulty — " + LEVEL_LABEL.get(difficulty),
                true, source, blankToNull(text(profile, RUBRIC.get(difficulty)))));
        layers.add(layer("persona", "persona", "Persona", true, source, blankToNull(text(profile, SlotKey.PERSONA))));
        layers.add(layer("reader_context", "reader", "Reader context", true, source,
                blankToNull(text(profile, SlotKey.READER_CONTEXT))));
        for (SlotKey fn : FUNCTION_SLOTS) {
            layers.add(layer(fn.wire(), "functions", FUNCTION_LAYER_LABEL.get(fn), true, source,
                    blankToNull(framed(fn, profile))));
        }
        layers.add(layer("capability_routing", "routing", "When BooKI acts on its own in chat", true, source,
                routingContent(profile, enabled)));
        layers.add(layer("session", "session", "This session", false, "Session", contextSessionFacts(session)));

        return new SessionContextResponse(
                profile != null ? profile.getId() : null,
                profile != null ? profile.getName() : null,
                language, difficulty,
                enabled.stream().sorted().map(Capability::wire).toList(),
                layers);
    }

    // ---- internals -------------------------------------------------------

    private static void appendSection(StringBuilder sb, String heading, String body) {
        if (body != null && !body.isBlank()) {
            sb.append("\n\n--- ").append(heading).append(" ---\n").append(body);
        }
    }

    private String framed(SlotKey key, AiProfile profile) {
        String body = text(profile, key);
        return Stream.of(key.lockedPreamble(), body.isBlank() ? null : body, key.lockedPostamble())
                .filter(s -> s != null && !s.isBlank())
                .reduce((a, b) -> a + "\n\n" + b)
                .orElse("");
    }

    private String sessionFacts(Session session) {
        return "Document: " + session.getDocument().getTitle()
                + "\nPages " + session.getStartPage() + "–" + session.getEndPage()
                + ", the reader is on page " + session.getCurrentPage()
                + "\nReply in " + languageName(session.getLanguage()) + ".";
    }

    private String contextSessionFacts(Session session) {
        return "Document: " + session.getDocument().getTitle()
                + "\nPages " + session.getStartPage() + "–" + session.getEndPage()
                + " · the reader is on page " + session.getCurrentPage()
                + "\nThe text of pages " + session.getStartPage() + "–" + session.getEndPage()
                + " is included in every answer.";
    }

    private String routingContent(AiProfile profile, Set<Capability> enabled) {
        String body = framed(SlotKey.CAPABILITY_ROUTING, profile);
        String enabledLine = "Enabled here: "
                + (enabled.isEmpty() ? "none"
                : enabled.stream().sorted().map(Capability::wire).reduce((a, b) -> a + ", " + b).orElse("none"))
                + ".";
        return body.isBlank() ? enabledLine : body + "\n\n" + enabledLine;
    }

    private static String text(AiProfile profile, SlotKey key) {
        return profile != null ? profile.text(key) : "";
    }

    private static SessionContextResponse.Layer layer(
            String key, String group, String label, boolean editable, String source, String content) {
        return new SessionContextResponse.Layer(key, group, label, editable, source, content);
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}
