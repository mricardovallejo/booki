package com.booki.prompt;

import com.booki.domain.AiProfile;
import com.booki.domain.Capability;
import com.booki.domain.Document;
import com.booki.domain.ReaderLevel;
import com.booki.domain.ReaderProfile;
import com.booki.domain.Session;
import com.booki.domain.SlotKey;
import com.booki.domain.User;
import com.booki.dto.SessionContextResponse;
import com.booki.service.ReaderProfileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.EnumSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class PromptAssemblerTest {

    private final SlotPromptCatalog catalog = new SlotPromptCatalog();

    @Mock
    private ReaderProfileService readerProfiles;

    private PromptAssembler assembler;

    @BeforeEach
    void setUp() {
        assembler = new PromptAssembler(readerProfiles, catalog);
    }

    private Session session(String difficulty, String language, boolean withProfile) {
        User user = new User();
        user.setId(1L);

        Document document = new Document();
        document.setTitle("Intro to Physics");

        Session s = new Session();
        s.setUser(user);
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

    private ReaderProfile reader(String name, ReaderLevel level, String context) {
        ReaderProfile r = new ReaderProfile();
        r.setId(3L);
        r.setName(name);
        r.setReaderLevel(level);
        r.setContext(context);
        return r;
    }

    @Test
    void forChatLayersCorePersonaRubricAndReaderContext() {
        Session s = session("hard", "es", true);
        lenient().when(readerProfiles.resolveFor(any()))
                .thenReturn(reader("Exam prep", ReaderLevel.INTERMEDIATE, "Prefers short answers."));

        String prompt = assembler.forChat(s, "PAGE TEXT");

        assertThat(prompt).contains("You are BooKI, a conversational reading companion"); // core
        assertThat(prompt).contains("Advanced. Assume a close reading");                    // rubric_hard
        assertThat(prompt).contains("You are a patient tutor");                             // persona
        assertThat(prompt).contains("Reader level: intermediate.\nPrefers short answers."); // reader profile
        assertThat(prompt).contains("Reply in Spanish.");                                   // session facts
        assertThat(prompt).contains("<<<BEGIN DOCUMENT>>>\nPAGE TEXT\n<<<END DOCUMENT>>>");  // fenced page text
        assertThat(prompt).endsWith("<<<END DOCUMENT>>>");
        assertThat(prompt).doesNotContain("Assume the reader is new to this material");
    }

    @Test
    void chatRoutingSectionCarriesTheLockedFrameAndTheEditableBody() {
        Session s = session("medium", "en", true);

        String section = assembler.chatRoutingSection(s);

        assertThat(section).contains("--- When BooKI can act on its own ---");
        assertThat(section).contains("respond with only {\"capability\":\"<name>\"}"); // locked frame
        assertThat(section).contains("Route only when the reader's latest message");  // editable body
    }

    @Test
    void chatRoutingSectionIsEmptyWithoutAProfile() {
        assertThat(assembler.chatRoutingSection(session("medium", "en", false))).isEmpty();
    }

    @Test
    void completeChatPlacesAllRoutingBeforeTheUntrustedDocument() {
        Session s = session("medium", "en", true);

        String prompt = assembler.forChat(s, "PAGE TEXT", "Enabled: quiz and explain.");

        assertThat(prompt).contains("--- Routing ---", "Enabled: quiz and explain.");
        assertThat(prompt.indexOf("--- Routing ---")).isLessThan(prompt.indexOf("<<<BEGIN DOCUMENT>>>"));
        assertThat(prompt).endsWith("<<<END DOCUMENT>>>");
    }

    @Test
    void forFunctionAddsTheLockedFrame() {
        Session s = session("easy", "en", true);
        lenient().when(readerProfiles.resolveFor(any())).thenReturn(null);

        String prompt = assembler.forFunction(s, SlotKey.FN_ANSWER_GRADING, "easy", "P");
        assertThat(prompt).contains("Reply in exactly three lines and nothing else:");
        assertThat(prompt).contains("CORRECT: yes or no");
        assertThat(prompt).contains("Judge whether the answer demonstrates understanding");
    }

    @Test
    void describeReturnsEveryLayerGrouped() {
        Session s = session("medium", "fr", true);
        lenient().when(readerProfiles.resolveFor(any())).thenReturn(reader("General reader", null, ""));

        SessionContextResponse ctx = assembler.describe(s);

        assertThat(ctx.aiProfileName()).isEqualTo("Patient Tutor");
        assertThat(ctx.readerProfileName()).isEqualTo("General reader");
        assertThat(ctx.language()).isEqualTo("fr");
        assertThat(ctx.difficulty()).isEqualTo("medium");
        assertThat(ctx.enabledCapabilities()).containsExactlyInAnyOrder("explain", "mnemonic", "quiz", "summary");
        assertThat(ctx.layers()).extracting(SessionContextResponse.Layer::group)
                .containsExactly("core", "difficulty", "persona", "reader",
                        "functions", "functions", "functions", "functions", "functions",
                        "routing", "session");
        assertThat(ctx.layers().get(0).editable()).isFalse();   // core
        assertThat(ctx.layers().get(0).source()).isEqualTo("App prompt catalog v1.0.0");
        assertThat(ctx.layers().get(1).content()).contains("Medium. Assume the reader");
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
