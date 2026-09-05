package com.booki.prompt;

import com.booki.domain.AiProfile;
import com.booki.domain.Capability;
import com.booki.domain.Document;
import com.booki.domain.Session;
import com.booki.domain.SlotKey;
import com.booki.domain.User;
import com.booki.dto.SessionContextResponse;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.assertj.core.api.Assertions.assertThat;

class PromptAssemblerTest {

    private final SlotPromptCatalog catalog = new SlotPromptCatalog();
    private final PromptAssembler assembler = new PromptAssembler();

    private Session session(String difficulty, String language, boolean withProfile) {
        User user = new User();
        user.setId(1L);

        Document document = new Document();
        document.setTitle("Intro to Physics");

        Session s = new Session();
        s.setDocument(document);
        s.setStartPage(1);
        s.setEndPage(4);
        s.setCurrentPage(2);
        s.setDifficulty(difficulty);
        s.setLanguage(language);
        if (withProfile) {
            AiProfile profile = catalog.newProfile(catalog.byKey("patient_tutor").orElseThrow(), user);
            profile.setId(9L);
            s.setAiProfile(profile);
        }
        return s;
    }

    @Test
    void forChatLayersCorePersonaAndTheActiveRubric() {
        String prompt = assembler.forChat(session("hard", "es", true), "PAGE TEXT");

        assertThat(prompt).contains("You are BooKI, a reading companion");                 // core
        assertThat(prompt).contains("Advanced: assume a close reading");                    // rubric_hard
        assertThat(prompt).contains("You are a patient tutor");                             // persona
        assertThat(prompt).contains("Reply in Spanish.");                                   // session facts
        assertThat(prompt).endsWith("DOCUMENT CONTEXT:\nPAGE TEXT");
        assertThat(prompt).doesNotContain("Easy: assume little prior knowledge");           // other rubrics not included
    }

    @Test
    void forFunctionAddsTheLockedFrame() {
        String prompt = assembler.forFunction(session("easy", "en", true), SlotKey.FN_ANSWER_GRADING, "easy", "P");
        assertThat(prompt).contains("Reply in exactly three lines and nothing else:");
        assertThat(prompt).contains("CORRECT: yes or no");
        assertThat(prompt).contains("Judge the reader's answer against the page");          // editable body
    }

    @Test
    void describeReturnsEveryLayerGrouped() {
        SessionContextResponse ctx = assembler.describe(session("medium", "fr", true));

        assertThat(ctx.aiProfileName()).isEqualTo("Patient Tutor");
        assertThat(ctx.language()).isEqualTo("fr");
        assertThat(ctx.difficulty()).isEqualTo("medium");
        assertThat(ctx.enabledCapabilities()).containsExactlyInAnyOrder("explain", "mnemonic", "quiz", "summary");
        assertThat(ctx.layers()).extracting(SessionContextResponse.Layer::group)
                .containsExactly("core", "difficulty", "persona", "reader",
                        "functions", "functions", "functions", "functions", "functions",
                        "routing", "session");
        assertThat(ctx.layers().get(0).editable()).isFalse();   // core
        assertThat(ctx.layers().get(1).content()).contains("Medium: assume the reader");
    }

    @Test
    void enabledCapabilitiesFallsBackToAllWithoutAProfile() {
        assertThat(assembler.enabledCapabilities(session("easy", "en", false)))
                .isEqualTo(EnumSet.allOf(Capability.class));
    }

    @Test
    void unknownLanguageAndDifficultyFallBack() {
        assertThat(assembler.resolveLanguage("de")).isEqualTo("en");
        assertThat(assembler.resolveDifficulty("brutal")).isEqualTo("medium");
    }
}
