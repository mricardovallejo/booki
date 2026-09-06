package com.booki.service.impl;

import com.booki.domain.ReaderProfile;
import com.booki.domain.User;
import com.booki.repository.ReaderProfileRepository;
import com.booki.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReaderProfileProvisionerTest {

    @Mock private ReaderProfileRepository readerProfiles;
    @Mock private UserRepository users;

    private ReaderProfileProvisioner provisioner;
    private User user;

    @BeforeEach
    void setUp() {
        provisioner = new ReaderProfileProvisioner(readerProfiles, users);
        user = new User();
        user.setId(7L);
    }

    @Test
    void createsAnEditableDefaultByCopyingTheGeneralTemplate() {
        ReaderProfile general = new ReaderProfile();
        general.setContext("General scaffold");
        when(readerProfiles.findFirstByUserIsNullAndDefaultProfileTrueOrderByIdAsc())
                .thenReturn(Optional.of(general));
        when(readerProfiles.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ReaderProfile created = provisioner.provisionFor(user);

        assertThat(created.getName()).isEqualTo("My reader profile");
        assertThat(created.getContext()).isEqualTo("General scaffold");
        assertThat(created.isReadOnly()).isFalse();
        assertThat(user.getDefaultReaderProfile()).isSameAs(created);
        verify(users).save(user);
    }
}
