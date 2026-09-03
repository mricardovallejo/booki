package com.booki.prompt;

import com.booki.domain.AiProfile;
import com.booki.domain.Capability;
import com.booki.domain.ReaderLevel;
import com.booki.domain.SlotKey;
import com.booki.domain.SlotPrompt;
import com.booki.domain.User;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The shipped AI Profile templates and the fixed BooKI core, in code (not the
 * database). "Improving a template" means editing this class; existing user
 * profiles keep their own rows and are never touched — see {@code docs/prompts.md}.
 *
 * <p>Dev-grade text for now; a professional pass is a later step.
 */
@Component
public class SlotPromptCatalog {

    public static final String CORE_PROMPT = """
            You are BooKI, a reading companion, not an authority. Always respond in the session's language, \
            whatever language these instructions are written in. Ground every answer in the session page range; \
            if something is not there, say so instead of guessing. Keep an encouraging tone and never scold a \
            wrong answer. When guidance conflicts, follow this order: these core rules, then the difficulty \
            rubric, then the function being performed, then the persona, then the reader context. A stated \
            accessibility need in the reader context outranks persona style.""";

    /** A shipped starting point. Not persisted — used only to seed and to restore user profiles. */
    public record Template(String key, String name, boolean isDefault, ReaderLevel readerLevel,
                           EnumSet<Capability> capabilities, Map<SlotKey, String> texts) {
    }

    private static final Map<SlotKey, String> SHARED = new EnumMap<>(SlotKey.class);

    static {
        SHARED.put(SlotKey.READER_CONTEXT, "");
        SHARED.put(SlotKey.RUBRIC_EASY, "Easy: assume little prior knowledge. Short sentences, common words. "
                + "Ask the reader to recall or restate one idea at a time. Accept partial answers and build on them.");
        SHARED.put(SlotKey.RUBRIC_MEDIUM, "Medium: assume the reader has read the pages once. Mix recall with "
                + "\"why\" and \"how\" questions. Expect two or three sentences. Name what is missing without "
                + "giving the full answer.");
        SHARED.put(SlotKey.RUBRIC_HARD, "Advanced: assume a close reading. Ask the reader to compare, evaluate, "
                + "or apply the ideas to a new case. Expect a precise, well-structured answer and hold it to "
                + "that standard.");
        SHARED.put(SlotKey.FN_QUIZ_QUESTION,
                "Ask one open reading-comprehension question about the current page, at the session difficulty.");
        SHARED.put(SlotKey.FN_ANSWER_GRADING, "Judge the reader's answer against the page. Lenient on Easy, "
                + "strict on Advanced. Keep feedback encouraging and specific.");
        SHARED.put(SlotKey.FN_SUMMARY, "Summarize the session pages and the discussion so far, at the requested "
                + "length. Lead with the main idea.");
        SHARED.put(SlotKey.FN_EXPLAIN, "Re-explain the idea the reader is stuck on in plainer terms, with one "
                + "concrete everyday analogy. Keep it to a short paragraph.");
        SHARED.put(SlotKey.FN_MNEMONIC, "Give one memory aid (acronym, vivid image, or short rhyme) for the key "
                + "points of these pages, then a one-line note on how to use it.");
        SHARED.put(SlotKey.CAPABILITY_ROUTING,
                "Available capabilities: quiz, summary, explain, mnemonic. Prefer a normal answer when unsure.");
    }

    private final List<Template> templates = List.of(
            template("patient_tutor", "Patient Tutor", true,
                    "You are a patient tutor. You explain one step at a time, check understanding before moving "
                            + "on, and never make the reader feel behind."),
            template("study_buddy", "Study Buddy", false,
                    "You are a study buddy the same age as the reader. You think out loud, ask questions back, "
                            + "and are happy to debate an idea."),
            template("subject_expert", "Subject Expert", false,
                    "You are a subject-matter expert. You use precise terms, define each one once, and connect "
                            + "the passage to the wider field."),
            template("accessible_pace", "Accessible Pace", false,
                    "You support readers who need a slower pace. You break ideas into small pieces, repeat key "
                            + "terms in different words, and give very concrete hints.")
    );

    private static Template template(String key, String name, boolean isDefault, String persona) {
        Map<SlotKey, String> texts = new EnumMap<>(SHARED);
        texts.put(SlotKey.PERSONA, persona);
        return new Template(key, name, isDefault, null, EnumSet.allOf(Capability.class), texts);
    }

    public List<Template> templates() {
        return templates;
    }

    public Optional<Template> byKey(String key) {
        return templates.stream().filter(t -> t.key().equals(key)).findFirst();
    }

    /** One editable {@link AiProfile} (with all its SlotPrompts) per template, for a new user. */
    public List<AiProfile> seedFor(User user) {
        return templates.stream().map(t -> newProfile(t, user)).toList();
    }

    public AiProfile newProfile(Template t, User user) {
        AiProfile profile = new AiProfile();
        profile.setUser(user);
        profile.setName(t.name());
        profile.setBasedOnTemplate(t.key());
        profile.setDefaultProfile(t.isDefault());
        profile.setReaderLevel(t.readerLevel());
        profile.setEnabledCapabilities(EnumSet.copyOf(t.capabilities()));
        for (SlotKey key : SlotKey.values()) {
            profile.addSlot(new SlotPrompt(key, t.texts().getOrDefault(key, "")));
        }
        return profile;
    }

    /** Reset an existing profile's editable fields back to its template. */
    public void restore(AiProfile profile) {
        Template t = byKey(profile.getBasedOnTemplate()).orElseThrow(
                () -> new IllegalArgumentException("This profile has no original template to restore from."));
        profile.setReaderLevel(t.readerLevel());
        profile.setEnabledCapabilities(EnumSet.copyOf(t.capabilities()));
        for (SlotPrompt slot : profile.getSlots()) {
            String text = t.texts().getOrDefault(slot.getSlot(), "");
            slot.setText(text);
            slot.setOriginalText(text);
        }
    }
}
