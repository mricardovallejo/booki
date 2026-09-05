package com.booki.service.impl;

import com.booki.domain.AiProfile;
import com.booki.domain.Capability;
import com.booki.domain.ReaderLevel;
import com.booki.domain.SlotKey;
import com.booki.domain.User;
import com.booki.dto.AiProfileResponse;
import com.booki.dto.UpdateAiProfileRequest;
import com.booki.prompt.SlotPromptCatalog;
import com.booki.repository.AiProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiProfileServiceImplTest {

    private static final long USER_ID = 7L;
    private static final long PROFILE_ID = 1L;

    @Mock private AiProfileRepository repository;
    private final SlotPromptCatalog catalog = new SlotPromptCatalog();

    private AiProfileServiceImpl service;
    private AiProfile profile;

    @BeforeEach
    void setUp() {
        service = new AiProfileServiceImpl(repository, catalog);

        User user = new User();
        user.setId(USER_ID);
        profile = catalog.newProfile(catalog.byKey("patient_tutor").orElseThrow(), user);
        profile.setId(PROFILE_ID);

        org.mockito.Mockito.lenient()
                .when(repository.findByIdAndUserId(PROFILE_ID, USER_ID)).thenReturn(Optional.of(profile));
        org.mockito.Mockito.lenient()
                .when(repository.save(any(AiProfile.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void getReturnsEverySlot() {
        AiProfileResponse response = service.get(USER_ID, PROFILE_ID);
        assertThat(response.name()).isEqualTo("Patient Tutor");
        assertThat(response.slots()).hasSize(SlotKey.values().length);
        assertThat(response.isDefault()).isTrue();
    }

    @Test
    void getUnknownProfileThrowsNotFound() {
        when(repository.findByIdAndUserId(99L, USER_ID)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.get(USER_ID, 99L)).isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void updateAppliesName_level_capabilities_andSlotText() {
        UpdateAiProfileRequest request = new UpdateAiProfileRequest();
        request.setName("  My Tutor  ");
        request.setReaderLevel("advanced");
        request.setEnabledCapabilities(List.of("quiz", "explain", "translate")); // translate ignored
        UpdateAiProfileRequest.SlotPatch patch = new UpdateAiProfileRequest.SlotPatch();
        patch.setKey("persona");
        patch.setText("A brand new persona.");
        request.setSlots(List.of(patch));

        AiProfileResponse response = service.update(USER_ID, PROFILE_ID, request);

        assertThat(response.name()).isEqualTo("My Tutor");
        assertThat(response.readerLevel()).isEqualTo("advanced");
        assertThat(response.enabledCapabilities()).containsExactlyInAnyOrder("quiz", "explain");
        assertThat(profile.text(SlotKey.PERSONA)).isEqualTo("A brand new persona.");
        assertThat(profile.slot(SlotKey.PERSONA).isModified()).isTrue();
    }

    @Test
    void updateClearsReaderLevelOnEmptyString() {
        profile.setReaderLevel(ReaderLevel.BEGINNER);
        UpdateAiProfileRequest request = new UpdateAiProfileRequest();
        request.setReaderLevel("");
        assertThat(service.update(USER_ID, PROFILE_ID, request).readerLevel()).isNull();
    }

    @Test
    void updateIgnoresUnknownSlotKey() {
        UpdateAiProfileRequest request = new UpdateAiProfileRequest();
        UpdateAiProfileRequest.SlotPatch patch = new UpdateAiProfileRequest.SlotPatch();
        patch.setKey("does_not_exist");
        patch.setText("x");
        request.setSlots(List.of(patch));
        assertThat(service.update(USER_ID, PROFILE_ID, request).slots()).hasSize(SlotKey.values().length);
    }

    @Test
    void duplicateCopiesFieldsAndSlotsAndClearsDefault() {
        profile.setReaderLevel(ReaderLevel.INTERMEDIATE);
        profile.slot(SlotKey.PERSONA).setText("edited persona");

        AiProfileResponse copy = service.duplicate(USER_ID, PROFILE_ID, null);

        assertThat(copy.name()).isEqualTo("Patient Tutor (copy)");
        assertThat(copy.isDefault()).isFalse();
        assertThat(copy.readerLevel()).isEqualTo("intermediate");
        var persona = copy.slots().stream().filter(s -> s.key().equals("persona")).findFirst().orElseThrow();
        assertThat(persona.text()).isEqualTo("edited persona");
        assertThat(persona.originalText()).isNotEqualTo("edited persona"); // baseline carried over
        assertThat(persona.modified()).isTrue();
        verify(repository).save(any(AiProfile.class));
    }

    @Test
    void revertResetsOneSlotToItsOriginal() {
        profile.slot(SlotKey.RUBRIC_MEDIUM).setText("harder");
        service.revertSlot(USER_ID, PROFILE_ID, "rubric_medium");
        assertThat(profile.slot(SlotKey.RUBRIC_MEDIUM).isModified()).isFalse();
    }

    @Test
    void revertUnknownSlotIsRejected() {
        assertThatThrownBy(() -> service.revertSlot(USER_ID, PROFILE_ID, "bogus"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void restoreResetsWholeProfileButKeepsName() {
        profile.setName("Renamed");
        profile.setEnabledCapabilities(java.util.EnumSet.of(Capability.QUIZ));
        profile.slot(SlotKey.PERSONA).setText("changed");

        AiProfileResponse response = service.restore(USER_ID, PROFILE_ID);

        assertThat(response.name()).isEqualTo("Renamed");
        assertThat(response.enabledCapabilities()).containsExactlyInAnyOrder("quiz", "summary", "explain", "mnemonic");
        assertThat(profile.slot(SlotKey.PERSONA).isModified()).isFalse();
    }

    @Test
    void deleteRejectedWhenItIsTheOnlyProfile() {
        when(repository.countByUserId(USER_ID)).thenReturn(1L);
        assertThatThrownBy(() -> service.delete(USER_ID, PROFILE_ID)).isInstanceOf(IllegalArgumentException.class);
        verify(repository, times(0)).delete(any());
    }

    @Test
    void deleteRemovesProfileWhenMoreThanOne() {
        when(repository.countByUserId(USER_ID)).thenReturn(3L);
        service.delete(USER_ID, PROFILE_ID);
        verify(repository).delete(profile);
    }
}
