package com.booki.prompt;

import com.booki.domain.SlotKey;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SlotPromptCatalogTest {

    private final SlotPromptCatalog catalog = new SlotPromptCatalog();

    @Test
    void loadsTheVersionedCatalogAndEveryRequiredSlot() {
        assertThat(catalog.version()).isEqualTo("1.0.0");
        assertThat(catalog.corePrompt()).contains("conversational reading companion", "SOURCE DISCIPLINE");
        assertThat(catalog.templates()).extracting(SlotPromptCatalog.Template::key)
                .containsExactly("patient_tutor", "study_buddy", "subject_expert", "accessible_pace",
                        "dyslexia_friendly_guide");
        assertThat(catalog.templates()).filteredOn(SlotPromptCatalog.Template::isDefault).hasSize(1);
        assertThat(catalog.templates()).allSatisfy(template ->
                assertThat(template.texts()).containsKeys(SlotKey.values()));
    }
}
