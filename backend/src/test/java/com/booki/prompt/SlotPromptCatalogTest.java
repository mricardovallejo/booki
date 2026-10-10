package com.booki.prompt;

import com.booki.domain.AppProfileSlot;
import com.booki.domain.AppProfileSlotKey;
import com.booki.domain.User;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SlotPromptCatalogTest {

    private final SlotPromptCatalog catalog = new SlotPromptCatalog();

    @Test
    void loadsTheVersionedCatalogAndEveryTemplate() {
        assertThat(catalog.version()).isEqualTo("1.5.0");
        assertThat(catalog.corePrompt()).contains("conversational reading companion", "SOURCE DISCIPLINE");
        assertThat(catalog.templates()).extracting(SlotPromptCatalog.Template::key)
                .containsExactly("patient_tutor", "study_buddy", "subject_expert", "accessible_pace",
                        "language_learning_guide");
        assertThat(catalog.templates()).filteredOn(SlotPromptCatalog.Template::isDefault).hasSize(1);
        // A template now varies only by persona — nothing else is per-tutor.
        assertThat(catalog.byKey("patient_tutor").orElseThrow().personaText()).contains("You are a patient tutor");
    }

    @Test
    void appProfileDefaultsCoverAllNineKeysAccountWide() {
        assertThat(catalog.appProfileDefaults()).containsOnlyKeys(AppProfileSlotKey.values());
        assertThat(catalog.appProfileDefaults().get(AppProfileSlotKey.HARD))
                .contains("Advanced. Assume a close reading");
        assertThat(catalog.appProfileDefaults().get(AppProfileSlotKey.FN_QUIZ_QUESTION))
                .contains("Do not use another part of the document");
    }

    @Test
    void newAppProfileSlotsSeedsEveryKeyFromTheShippedDefaults() {
        User user = new User();
        user.setId(1L);

        List<AppProfileSlot> slots = catalog.newAppProfileSlots(user);

        assertThat(slots).extracting(AppProfileSlot::getSlot).containsExactly(AppProfileSlotKey.values());
        assertThat(slots).allSatisfy(s -> assertThat(s.getText()).isEqualTo(s.getOriginalText()).isNotBlank());
    }
}
