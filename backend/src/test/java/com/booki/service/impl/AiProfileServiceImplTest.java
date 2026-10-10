package com.booki.service.impl;

import com.booki.domain.AiProfile;
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
        profile = catalog.newProfile(catalog.byKey("booki").orElseThrow(), user);
        profile.setId(PROFILE_ID);

        org.mockito.Mockito.lenient()
                .when(repository.findByIdAndUserId(PROFILE_ID, USER_ID)).thenReturn(Optional.of(profile));
        org.mockito.Mockito.lenient()
                .when(repository.save(any(AiProfile.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void getReturnsThePersona() {
        AiProfileResponse response = service.get(USER_ID, PROFILE_ID);
        assertThat(response.name()).isEqualTo("BooKI");
        assertThat(response.persona()).contains("You are BooKI's own voice");
        assertThat(response.personaModified()).isFalse();
        assertThat(response.isDefault()).isTrue();
    }

    @Test
    void getUnknownProfileThrowsNotFound() {
        when(repository.findByIdAndUserId(99L, USER_ID)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.get(USER_ID, 99L)).isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void updateAppliesNameAndPersona() {
        UpdateAiProfileRequest request = new UpdateAiProfileRequest();
        request.setName("  My Tutor  ");
        request.setPersona("A brand new persona.");

        AiProfileResponse response = service.update(USER_ID, PROFILE_ID, request);

        assertThat(response.name()).isEqualTo("My Tutor");
        assertThat(profile.getPersonaText()).isEqualTo("A brand new persona.");
        assertThat(profile.isPersonaModified()).isTrue();
    }

    @Test
    void duplicateCopiesPersonaAndClearsDefault() {
        profile.setPersonaText("edited persona");

        AiProfileResponse copy = service.duplicate(USER_ID, PROFILE_ID, null);

        assertThat(copy.name()).isEqualTo("BooKI (copy)");
        assertThat(copy.isDefault()).isFalse();
        assertThat(copy.persona()).isEqualTo("edited persona");
        assertThat(copy.personaOriginal()).isNotEqualTo("edited persona"); // baseline carried over
        assertThat(copy.personaModified()).isTrue();
        verify(repository).save(any(AiProfile.class));
    }

    @Test
    void revertResetsPersonaToItsOriginal() {
        profile.setPersonaText("different wording");
        service.revertPersona(USER_ID, PROFILE_ID);
        assertThat(profile.isPersonaModified()).isFalse();
    }

    @Test
    void restoreResetsPersonaButKeepsName() {
        profile.setName("Renamed");
        profile.setPersonaText("changed");

        AiProfileResponse response = service.restore(USER_ID, PROFILE_ID);

        assertThat(response.name()).isEqualTo("Renamed");
        assertThat(profile.isPersonaModified()).isFalse();
    }

    @Test
    void updateSetsDefaultAndClearsThePreviousOne() {
        AiProfile other = catalog.newProfile(catalog.byKey("teacher").orElseThrow(), profile.getUser());
        other.setId(2L);
        other.setDefaultProfile(true);
        profile.setDefaultProfile(false);
        when(repository.findFirstByUserIdAndDefaultProfileTrueOrderByIdAsc(USER_ID))
                .thenReturn(Optional.of(other));

        UpdateAiProfileRequest request = new UpdateAiProfileRequest();
        request.setIsDefault(true);
        AiProfileResponse response = service.update(USER_ID, PROFILE_ID, request);

        assertThat(response.isDefault()).isTrue();
        assertThat(other.isDefaultProfile()).isFalse();
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

    @Test
    void deletingTheDefaultPromotesAnotherProfile() {
        AiProfile other = catalog.newProfile(catalog.byKey("teacher").orElseThrow(), profile.getUser());
        other.setId(2L);
        when(repository.countByUserId(USER_ID)).thenReturn(2L);
        when(repository.findByUserIdOrderByIdAsc(USER_ID)).thenReturn(List.of(other));

        service.delete(USER_ID, PROFILE_ID);

        assertThat(other.isDefaultProfile()).isTrue();
        verify(repository).save(other);
    }
}
