package com.booki.prompt;

import com.booki.domain.AiProfile;
import com.booki.domain.User;
import com.booki.repository.AiProfileRepository;
import com.booki.repository.SessionRepository;
import com.booki.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiProfileBackfillTest {

    @Mock private UserRepository userRepository;
    @Mock private AiProfileRepository aiProfileRepository;
    @Mock private SessionRepository sessionRepository;

    private final SlotPromptCatalog catalog = new SlotPromptCatalog();

    @Test
    @SuppressWarnings("unchecked")
    void addsOnlyMissingTemplatesWithoutReplacingTheExistingDefault() {
        User user = user(7L);
        AiProfile existing = catalog.newProfile(catalog.defaultTemplate(), user);
        stub(user, existing);

        backfill().seedMissing();

        List<AiProfile> added = captureSaved();
        assertThat(added).extracting(AiProfile::getBasedOnTemplate)
                .containsExactly("teacher", "critic", "step_by_step", "specialist");
        assertThat(added).noneMatch(AiProfile::isDefaultProfile);
        assertThat(existing.isDefaultProfile()).isTrue();
    }

    @Test
    void unEditedLiveCopyFollowsTheCatalogButAnEditedOneIsKept() {
        User user = user(7L);
        AiProfile stale = legacy(1L, user, "teacher", "Teacher", "old teacher text", "old teacher text", true);
        AiProfile edited = legacy(2L, user, "critic", "Critic", "my own critic", "old critic text", false);
        stub(user, stale, edited);

        backfill().seedMissing();

        assertThat(stale.getPersonaText()).isEqualTo(catalog.byKey("teacher").orElseThrow().personaText());
        assertThat(stale.getPersonaOriginalText()).isEqualTo(stale.getPersonaText());
        assertThat(edited.getPersonaText()).isEqualTo("my own critic");
        assertThat(edited.getPersonaOriginalText()).isEqualTo("old critic text");
    }

    @Test
    void retiredTemplatesAreConvertedInPlaceAndTheDefaultMovesToBooki() {
        User user = user(7L);
        AiProfile tutor = legacy(1L, user, "patient_tutor", "Patient Tutor", "old", "old", true);
        AiProfile buddy = legacy(2L, user, "study_buddy", "Study Buddy", "old", "old", false);
        AiProfile expert = legacy(3L, user, "subject_expert", "Subject Expert", "edited", "old", false);
        AiProfile pace = legacy(4L, user, "accessible_pace", "Accessible Pace", "old", "old", false);
        AiProfile guideInUse = legacy(5L, user, "language_learning_guide", "Language Guide", "old", "old", false);
        AiProfile guideUnused = legacy(6L, user, "language_learning_guide", "Language Guide", "old", "old", false);
        stub(user, tutor, buddy, expert, pace, guideInUse, guideUnused);
        when(sessionRepository.existsByAiProfileId(5L)).thenReturn(true);
        when(sessionRepository.existsByAiProfileId(6L)).thenReturn(false);

        backfill().seedMissing();

        // Same rows, new identity — sessions pointing at them keep working.
        assertThat(tutor.getBasedOnTemplate()).isEqualTo("teacher");
        assertThat(tutor.getName()).isEqualTo("Teacher");
        assertThat(buddy.getBasedOnTemplate()).isEqualTo("booki");
        assertThat(pace.getBasedOnTemplate()).isEqualTo("specialist");
        assertThat(pace.getPersonaText()).isEqualTo(catalog.byKey("specialist").orElseThrow().personaText());
        // Edited: untouched, even though its template is retired.
        assertThat(expert.getBasedOnTemplate()).isEqualTo("subject_expert");
        assertThat(expert.getPersonaText()).isEqualTo("edited");
        // Replacement already present: a used copy stays, an unused one goes.
        assertThat(guideInUse.getBasedOnTemplate()).isEqualTo("language_learning_guide");
        verify(aiProfileRepository).delete(guideUnused);
        verify(aiProfileRepository, never()).delete(guideInUse);
        // Missing live templates seeded; the converted default moves to BooKI.
        assertThat(captureSaved()).extracting(AiProfile::getBasedOnTemplate)
                .containsExactly("critic", "step_by_step");
        assertThat(buddy.isDefaultProfile()).isTrue();
        assertThat(tutor.isDefaultProfile()).isFalse();
    }

    @Test
    void aDefaultChosenAfterTheUpgradeIsNeverOverridden() {
        User user = user(7L);
        List<AiProfile> profiles = new ArrayList<>(catalog.seedFor(user));
        profiles.forEach(p -> p.setDefaultProfile("critic".equals(p.getBasedOnTemplate())));
        stub(user, profiles.toArray(AiProfile[]::new));

        backfill().seedMissing();

        assertThat(profiles).filteredOn(AiProfile::isDefaultProfile)
                .singleElement().extracting(AiProfile::getBasedOnTemplate).isEqualTo("critic");
        verify(aiProfileRepository, never()).saveAll(any());
    }

    private AiProfileBackfill backfill() {
        return new AiProfileBackfill(userRepository, aiProfileRepository, sessionRepository, catalog);
    }

    private void stub(User user, AiProfile... profiles) {
        when(userRepository.findAll()).thenReturn(List.of(user));
        when(aiProfileRepository.findByUserIdOrderByIdAsc(user.getId())).thenReturn(List.of(profiles));
    }

    @SuppressWarnings("unchecked")
    private List<AiProfile> captureSaved() {
        ArgumentCaptor<Iterable<AiProfile>> saved = ArgumentCaptor.forClass(Iterable.class);
        verify(aiProfileRepository).saveAll(saved.capture());
        return StreamSupport.stream(saved.getValue().spliterator(), false).toList();
    }

    private static User user(Long id) {
        User user = new User();
        user.setId(id);
        return user;
    }

    private static AiProfile legacy(Long id, User user, String key, String name, String persona, String original,
                                    boolean isDefault) {
        AiProfile profile = new AiProfile();
        profile.setId(id);
        profile.setUser(user);
        profile.setBasedOnTemplate(key);
        profile.setName(name);
        profile.setPersonaText(persona);
        profile.setPersonaOriginalText(original);
        profile.setDefaultProfile(isDefault);
        return profile;
    }
}
