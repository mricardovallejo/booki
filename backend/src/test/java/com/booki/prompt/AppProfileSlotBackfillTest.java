package com.booki.prompt;

import com.booki.domain.AppProfileSlot;
import com.booki.domain.AppProfileSlotKey;
import com.booki.domain.User;
import com.booki.repository.AppProfileSlotRepository;
import com.booki.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppProfileSlotBackfillTest {

    @Mock private UserRepository userRepository;
    @Mock private AppProfileSlotRepository appProfileSlotRepository;

    @Test
    void unEditedRowsFollowTheCatalogAndEditedRowsAreKept() {
        SlotPromptCatalog catalog = new SlotPromptCatalog();
        User user = new User();
        user.setId(3L);
        List<AppProfileSlot> rows = catalog.newAppProfileSlots(user);
        AppProfileSlot stale = row(rows, AppProfileSlotKey.FN_SUMMARY);
        stale.setText("old summary");
        stale.setOriginalText("old summary");
        AppProfileSlot edited = row(rows, AppProfileSlotKey.HARD);
        edited.setOriginalText("old hard");
        edited.setText("my own hard rubric");

        when(userRepository.findAll()).thenReturn(List.of(user));
        when(appProfileSlotRepository.findByUserId(3L)).thenReturn(rows);

        new AppProfileSlotBackfill(userRepository, appProfileSlotRepository, catalog).seedMissing();

        String current = catalog.appProfileDefaults().get(AppProfileSlotKey.FN_SUMMARY);
        assertThat(stale.getText()).isEqualTo(current);
        assertThat(stale.getOriginalText()).isEqualTo(current);
        assertThat(edited.getText()).isEqualTo("my own hard rubric");
        assertThat(edited.getOriginalText()).isEqualTo("old hard");
    }

    private static AppProfileSlot row(List<AppProfileSlot> rows, AppProfileSlotKey key) {
        return rows.stream().filter(r -> r.getSlot() == key).findFirst().orElseThrow();
    }
}
