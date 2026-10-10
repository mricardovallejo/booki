package com.booki.service.impl;

import com.booki.domain.AppProfileSlot;
import com.booki.domain.AppProfileSlotKey;
import com.booki.domain.User;
import com.booki.dto.AppProfileSlotResponse;
import com.booki.dto.UpdateAppProfileRequest;
import com.booki.repository.AppProfileSlotRepository;
import com.booki.service.AppProfileSlotService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AppProfileSlotServiceImpl implements AppProfileSlotService {

    private final AppProfileSlotRepository repository;

    @Override
    @Transactional(readOnly = true)
    public List<AppProfileSlotResponse> list(Long userId) {
        return toResponses(repository.findByUserId(userId));
    }

    @Override
    @Transactional
    public List<AppProfileSlotResponse> update(Long userId, UpdateAppProfileRequest request) {
        List<AppProfileSlot> rows = repository.findByUserId(userId);
        if (request.getSlots() != null) {
            for (var patch : request.getSlots()) {
                if (patch.getKey() == null || patch.getText() == null) {
                    continue;
                }
                AppProfileSlotKey key;
                try {
                    key = AppProfileSlotKey.ofWire(patch.getKey());
                } catch (IllegalArgumentException e) {
                    continue;
                }
                rows.stream().filter(r -> r.getSlot() == key).findFirst()
                        .ifPresent(r -> r.setText(patch.getText()));
            }
        }
        return toResponses(rows);
    }

    @Override
    @Transactional
    public List<AppProfileSlotResponse> revert(Long userId, String key) {
        List<AppProfileSlot> rows = repository.findByUserId(userId);
        AppProfileSlotKey slotKey;
        try {
            slotKey = AppProfileSlotKey.ofWire(key);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown App Profile slot: " + key);
        }
        rows.stream().filter(r -> r.getSlot() == slotKey).findFirst()
                .ifPresent(r -> r.setText(r.getOriginalText()));
        return toResponses(rows);
    }

    @Override
    @Transactional
    public List<AppProfileSlotResponse> restore(Long userId) {
        List<AppProfileSlot> rows = repository.findByUserId(userId);
        rows.forEach(r -> r.setText(r.getOriginalText()));
        return toResponses(rows);
    }

    @Override
    @Transactional(readOnly = true)
    public String textFor(User user, AppProfileSlotKey key) {
        return repository.findByUserIdAndSlot(user.getId(), key).map(AppProfileSlot::getText).orElse("");
    }

    private List<AppProfileSlotResponse> toResponses(List<AppProfileSlot> rows) {
        return rows.stream()
                .sorted(Comparator.comparingInt(r -> r.getSlot().ordinal()))
                .map(r -> new AppProfileSlotResponse(
                        r.getSlot().wire(), r.getSlot().label(), r.getSlot().group().wire(),
                        r.getSlot().lockedPreamble(), r.getSlot().lockedPostamble(),
                        r.getText(), r.getOriginalText(), r.isModified()))
                .toList();
    }
}
