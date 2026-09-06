package com.booki.prompt;

import com.booki.domain.AiProfile;
import com.booki.domain.User;
import com.booki.repository.AiProfileRepository;
import com.booki.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiProfileBackfillTest {

    @Mock private UserRepository userRepository;
    @Mock private AiProfileRepository aiProfileRepository;

    @Test
    @SuppressWarnings("unchecked")
    void addsOnlyMissingTemplatesWithoutReplacingTheExistingDefault() {
        SlotPromptCatalog catalog = new SlotPromptCatalog();
        User user = new User();
        user.setId(7L);
        AiProfile existing = catalog.newProfile(catalog.byKey("patient_tutor").orElseThrow(), user);

        when(userRepository.findAll()).thenReturn(List.of(user));
        when(aiProfileRepository.findByUserIdOrderByIdAsc(7L)).thenReturn(List.of(existing));

        new AiProfileBackfill(userRepository, aiProfileRepository, catalog).seedMissing();

        ArgumentCaptor<Iterable<AiProfile>> saved = ArgumentCaptor.forClass(Iterable.class);
        verify(aiProfileRepository).saveAll(saved.capture());
        List<AiProfile> added = StreamSupport.stream(saved.getValue().spliterator(), false).toList();

        assertThat(added).extracting(AiProfile::getBasedOnTemplate)
                .containsExactly("study_buddy", "subject_expert", "accessible_pace",
                        "language_learning_guide");
        assertThat(added).noneMatch(AiProfile::isDefaultProfile);
        assertThat(existing.isDefaultProfile()).isTrue();
    }
}
