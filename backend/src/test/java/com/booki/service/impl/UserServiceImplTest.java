package com.booki.service.impl;

import com.booki.domain.Capability;
import com.booki.domain.User;
import com.booki.dto.UpdateUserRequest;
import com.booki.dto.UserResponse;
import com.booki.email.ReportRecipientResolver;
import com.booki.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    private static final long USER_ID = 7L;

    @Mock private UserRepository repository;
    private UserServiceImpl service;
    private User user;

    @BeforeEach
    void setUp() {
        service = new UserServiceImpl(repository, new ReportRecipientResolver(""));

        user = new User();
        user.setId(USER_ID);
        user.setEmail("reader@example.com");
        user.setName("Reader");

        when(repository.findById(USER_ID)).thenReturn(Optional.of(user));
        lenient().when(repository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void getCurrentUserReturnsTheAccountWideCapabilities() {
        UserResponse response = service.getCurrentUser(USER_ID);
        assertThat(response.getEnabledCapabilities())
                .containsExactlyInAnyOrder("quiz", "summary", "explain", "mnemonic");
    }

    @Test
    void updateCurrentUserAppliesNameAndCapabilitiesIgnoringUnknownOnes() {
        UpdateUserRequest request = new UpdateUserRequest();
        request.setName("  New Name  ");
        request.setEnabledCapabilities(List.of("quiz", "explain", "translate")); // translate ignored

        UserResponse response = service.updateCurrentUser(USER_ID, request);

        assertThat(response.getName()).isEqualTo("New Name");
        assertThat(response.getEnabledCapabilities()).containsExactlyInAnyOrder("quiz", "explain");
        assertThat(user.getEnabledCapabilities()).isEqualTo(EnumSet.of(Capability.QUIZ, Capability.EXPLAIN));
    }

    @Test
    void updateCurrentUserLeavesFieldsUnchangedWhenNotProvided() {
        UpdateUserRequest request = new UpdateUserRequest();
        UserResponse response = service.updateCurrentUser(USER_ID, request);

        assertThat(response.getName()).isEqualTo("Reader");
        assertThat(response.getEnabledCapabilities())
                .containsExactlyInAnyOrder("quiz", "summary", "explain", "mnemonic");
    }

    @Test
    void defaultReportEmailAcceptsASemicolonSeparatedListAndNormalizesSpacing() {
        UpdateUserRequest request = new UpdateUserRequest();
        request.setDefaultReportEmail("parent@example.com;  tutor@example.com ");

        UserResponse response = service.updateCurrentUser(USER_ID, request);

        assertThat(response.getDefaultReportEmail()).isEqualTo("parent@example.com; tutor@example.com");
        assertThat(response.getEffectiveReportEmail()).isEqualTo("parent@example.com; tutor@example.com");
    }

    @Test
    void emptyStringClearsDefaultReportEmailBackToNull() {
        user.setDefaultReportEmail("old@example.com");
        UpdateUserRequest request = new UpdateUserRequest();
        request.setDefaultReportEmail("  ");

        UserResponse response = service.updateCurrentUser(USER_ID, request);

        assertThat(response.getDefaultReportEmail()).isNull();
    }

    @Test
    void invalidDefaultReportEmailIsRejected() {
        UpdateUserRequest request = new UpdateUserRequest();
        request.setDefaultReportEmail("not-an-email");

        assertThatThrownBy(() -> service.updateCurrentUser(USER_ID, request))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
