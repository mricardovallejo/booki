package com.booki.service.impl;

import com.booki.domain.ReaderProfile;
import com.booki.domain.User;
import com.booki.repository.ReaderProfileRepository;
import com.booki.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReaderProfileBackfillTest {

    @Mock private UserRepository userRepository;
    @Mock private ReaderProfileRepository readerProfileRepository;
    @Mock private ReaderProfileProvisioner provisioner;

    @Test
    void leavesAUserWithADefaultAlreadySetUntouched() {
        User user = new User();
        user.setId(1L);
        ReaderProfile existingDefault = new ReaderProfile();
        existingDefault.setId(10L);
        user.setDefaultReaderProfile(existingDefault);
        when(userRepository.findAll()).thenReturn(List.of(user));

        new ReaderProfileBackfill(userRepository, readerProfileRepository, provisioner).seedMissing();

        verify(readerProfileRepository, never()).findByUserIdOrderByIdAsc(1L);
        verify(provisioner, never()).provisionFor(user);
        verify(userRepository, never()).save(user);
    }

    @Test
    void pointsTheDefaultAtAnOwnedProfileThatWasNeverMarkedDefault() {
        User user = new User();
        user.setId(2L);
        ReaderProfile owned = new ReaderProfile();
        owned.setId(20L);
        when(userRepository.findAll()).thenReturn(List.of(user));
        when(readerProfileRepository.findByUserIdOrderByIdAsc(2L)).thenReturn(List.of(owned));

        new ReaderProfileBackfill(userRepository, readerProfileRepository, provisioner).seedMissing();

        assertThat(user.getDefaultReaderProfile()).isEqualTo(owned);
        verify(userRepository).save(user);
        verify(provisioner, never()).provisionFor(user);
    }

    @Test
    void provisionsAFreshProfileForAUserWithNoneAtAll() {
        User user = new User();
        user.setId(3L);
        when(userRepository.findAll()).thenReturn(List.of(user));
        when(readerProfileRepository.findByUserIdOrderByIdAsc(3L)).thenReturn(List.of());

        new ReaderProfileBackfill(userRepository, readerProfileRepository, provisioner).seedMissing();

        verify(provisioner).provisionFor(user);
    }
}
