package com.booki.prompt;

import com.booki.domain.SlotKey;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SlotPromptCatalogTest {

    private final SlotPromptCatalog catalog = new SlotPromptCatalog();

    @Test
    void loadsTheVersionedCatalogAndEveryRequiredSlot() {
        assertThat(catalog.version()).isEqualTo("1.1.0");
        assertThat(catalog.corePrompt()).contains("conversational reading companion", "SOURCE DISCIPLINE");
        assertThat(catalog.templates()).extracting(SlotPromptCatalog.Template::key)
                .containsExactly("patient_tutor", "study_buddy", "subject_expert", "accessible_pace",
                        "language_learning_guide");
        assertThat(catalog.templates()).filteredOn(SlotPromptCatalog.Template::isDefault).hasSize(1);
        assertThat(catalog.templates()).allSatisfy(template ->
                assertThat(template.texts()).containsKeys(SlotKey.values()));
    }

    @Test
    void languageAndLearningGuideOverridesAllThreeDifficultyRubrics() {
        SlotPromptCatalog.Template guide = catalog.byKey("language_learning_guide").orElseThrow();
        SlotPromptCatalog.Template standard = catalog.byKey("patient_tutor").orElseThrow();

        assertThat(guide.texts().get(SlotKey.RUBRIC_EASY))
                .contains("response load")
                .isNotEqualTo(standard.texts().get(SlotKey.RUBRIC_EASY));
        assertThat(guide.texts().get(SlotKey.RUBRIC_MEDIUM)).contains("graduated support");
        assertThat(guide.texts().get(SlotKey.RUBRIC_HARD)).contains("conceptual challenge");
    }
}
