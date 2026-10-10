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
        assertThat(catalog.version()).isEqualTo("2.0.0");
        assertThat(catalog.corePrompt()).contains("makes reading feel worth continuing", "SOURCES AND CURIOSITY");
        assertThat(catalog.templates()).extracting(SlotPromptCatalog.Template::key)
                .containsExactly("booki", "teacher", "critic", "step_by_step", "specialist");
        assertThat(catalog.templates()).filteredOn(SlotPromptCatalog.Template::isDefault).hasSize(1);
        // A template now varies only by persona — nothing else is per-tutor.
        assertThat(catalog.byKey("teacher").orElseThrow().personaText()).contains("You are a clear, structured teacher");
        assertThat(catalog.defaultTemplate().key()).isEqualTo("booki");
    }

    @Test
    void retiredTemplateKeysMapToTheirReplacement() {
        assertThat(catalog.replacementFor("patient_tutor")).map(SlotPromptCatalog.Template::key).contains("teacher");
        assertThat(catalog.replacementFor("study_buddy")).map(SlotPromptCatalog.Template::key).contains("booki");
        assertThat(catalog.replacementFor("subject_expert")).map(SlotPromptCatalog.Template::key).contains("critic");
        assertThat(catalog.replacementFor("accessible_pace")).map(SlotPromptCatalog.Template::key).contains("specialist");
        assertThat(catalog.replacementFor("language_learning_guide")).map(SlotPromptCatalog.Template::key)
                .contains("specialist");
        assertThat(catalog.replacementFor("booki")).isEmpty();
    }

    @Test
    void appProfileDefaultsCoverAllNineKeysAccountWide() {
        assertThat(catalog.appProfileDefaults()).containsOnlyKeys(AppProfileSlotKey.values());
        assertThat(catalog.appProfileDefaults().get(AppProfileSlotKey.HARD))
                .contains("Hard. ASK the reader to evaluate");
        assertThat(catalog.appProfileDefaults().get(AppProfileSlotKey.FN_QUIZ_QUESTION))
                .contains("answerable entirely from these pages");
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
