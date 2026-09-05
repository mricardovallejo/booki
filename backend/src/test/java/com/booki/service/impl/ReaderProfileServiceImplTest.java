package com.booki.service.impl;

import com.booki.domain.ReaderLevel;
import com.booki.domain.ReaderProfile;
import com.booki.domain.Session;
import com.booki.domain.User;
import com.booki.dto.CreateReaderProfileRequest;
import com.booki.dto.ReaderProfileResponse;
import com.booki.dto.UpdateReaderProfileRequest;
import com.booki.repository.ReaderProfileRepository;
import com.booki.repository.UserRepository;
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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReaderProfileServiceImplTest {

    private static final long USER_ID = 7L;

    @Mock private ReaderProfileRepository repository;
    @Mock private UserRepository userRepository;

    private ReaderProfileServiceImpl service;
    private ReaderProfile builtIn;
    private ReaderProfile mine;

    @BeforeEach
    void setUp() {
        service = new ReaderProfileServiceImpl(repository, userRepository);

        User user = new User();
        user.setId(USER_ID);

        builtIn = new ReaderProfile();
        builtIn.setId(1L);
        builtIn.setName("General reader");
        builtIn.setDefaultProfile(true);
        builtIn.setReadOnly(true);

        mine = new ReaderProfile();
        mine.setId(5L);
        mine.setUser(user);
        mine.setName("Sciences");
        mine.setReaderLevel(ReaderLevel.ADVANCED);
        mine.setContext("PhD, terse answers.");

        lenient().when(repository.visibleTo(USER_ID)).thenReturn(List.of(builtIn, mine));
        lenient().when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        lenient().when(repository.save(any(ReaderProfile.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void listReturnsBuiltInAndOwn() {
        List<ReaderProfileResponse> list = service.list(USER_ID);
        assertThat(list).extracting(ReaderProfileResponse::name).containsExactly("General reader", "Sciences");
        assertThat(list.get(0).readOnly()).isTrue();
        assertThat(list.get(1).readerLevel()).isEqualTo("advanced");
    }

    @Test
    void createCopiesContextFromFromId() {
        CreateReaderProfileRequest req = new CreateReaderProfileRequest();
        req.setName("Sciences copy");
        req.setFromId(5L);

        ReaderProfileResponse created = service.create(USER_ID, req);

        assertThat(created.name()).isEqualTo("Sciences copy");
        assertThat(created.context()).isEqualTo("PhD, terse answers.");
        assertThat(created.readerLevel()).isEqualTo("advanced");
        assertThat(created.readOnly()).isFalse();
        assertThat(created.isDefault()).isFalse();
    }

    @Test
    void createWithoutFromIdUsesTheGenericScaffold() {
        CreateReaderProfileRequest req = new CreateReaderProfileRequest();
        req.setName("Blank");
        assertThat(service.create(USER_ID, req).context()).isEqualTo(ReaderProfileServiceImpl.GENERIC_CONTEXT);
    }

    @Test
    void updateTheBuiltInIsRejected() {
        when(repository.findByIdAndUserId(1L, USER_ID)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.update(USER_ID, 1L, new UpdateReaderProfileRequest()))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void updateClearsLevelOnEmptyString() {
        when(repository.findByIdAndUserId(5L, USER_ID)).thenReturn(Optional.of(mine));
        UpdateReaderProfileRequest req = new UpdateReaderProfileRequest();
        req.setReaderLevel("");
        assertThat(service.update(USER_ID, 5L, req).readerLevel()).isNull();
    }

    @Test
    void resolveForUsesTheSessionsProfileThenTheDefault() {
        User user = new User();
        user.setId(USER_ID);

        Session withReader = new Session();
        withReader.setUser(user);
        withReader.setReaderProfile(mine);
        assertThat(service.resolveFor(withReader).getName()).isEqualTo("Sciences");

        Session withoutReader = new Session();
        withoutReader.setUser(user);
        assertThat(service.resolveFor(withoutReader).getName()).isEqualTo("General reader"); // the default
    }

    @Test
    void forNewSessionFallsBackToTheDefaultForAnUnknownId() {
        User user = new User();
        user.setId(USER_ID);
        assertThat(service.forNewSession(USER_ID, 999L).getName()).isEqualTo("General reader");
    }
}
