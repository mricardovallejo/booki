package com.booki.service.impl;

import com.booki.domain.AiProfile;
import com.booki.domain.Capability;
import com.booki.domain.ReaderLevel;
import com.booki.domain.SlotKey;
import com.booki.domain.SlotPrompt;
import com.booki.dto.AiProfileResponse;
import com.booki.dto.AiProfileSummaryResponse;
import com.booki.dto.UpdateAiProfileRequest;
import com.booki.prompt.SlotPromptCatalog;
import com.booki.repository.AiProfileRepository;
import com.booki.service.AiProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AiProfileServiceImpl implements AiProfileService {

    private final AiProfileRepository repository;
    private final SlotPromptCatalog catalog;

    @Override
    public List<AiProfileSummaryResponse> list(Long userId) {
        return repository.findByUserIdOrderByIdAsc(userId).stream().map(AiProfileMapper::summary).toList();
    }

    @Override
    public AiProfileResponse get(Long userId, Long id) {
        return AiProfileMapper.full(owned(userId, id));
    }

    @Override
    @Transactional
    public AiProfileResponse update(Long userId, Long id, UpdateAiProfileRequest request) {
        AiProfile profile = owned(userId, id);

        if (request.getName() != null && !request.getName().isBlank()) {
            profile.setName(request.getName().trim());
        }
        if (request.getReaderLevel() != null) {
            profile.setReaderLevel(ReaderLevel.ofWire(request.getReaderLevel()));
        }
        if (request.getEnabledCapabilities() != null) {
            profile.setEnabledCapabilities(parseCapabilities(request.getEnabledCapabilities()));
        }
        if (request.getSlots() != null) {
            for (UpdateAiProfileRequest.SlotPatch patch : request.getSlots()) {
                if (patch.getKey() == null || patch.getText() == null) {
                    continue;
                }
                SlotKey key;
                try {
                    key = SlotKey.ofWire(patch.getKey());
                } catch (IllegalArgumentException e) {
                    continue;
                }
                SlotPrompt slot = profile.slot(key);
                if (slot != null) {
                    slot.setText(patch.getText());
                }
            }
        }
        return AiProfileMapper.full(repository.save(profile));
    }

    @Override
    @Transactional
    public AiProfileResponse duplicate(Long userId, Long id, String name) {
        AiProfile source = owned(userId, id);

        AiProfile copy = new AiProfile();
        copy.setUser(source.getUser());
        copy.setName((name != null && !name.isBlank()) ? name.trim() : source.getName() + " (copy)");
        copy.setBasedOnTemplate(source.getBasedOnTemplate());
        copy.setDefaultProfile(false);
        copy.setReaderLevel(source.getReaderLevel());
        copy.setEnabledCapabilities(copyOf(source.getEnabledCapabilities()));
        for (SlotPrompt slot : source.getSlots()) {
            SlotPrompt copied = new SlotPrompt(slot.getSlot(), slot.getText());
            copied.setOriginalText(slot.getOriginalText());
            copy.addSlot(copied);
        }
        return AiProfileMapper.full(repository.save(copy));
    }

    @Override
    @Transactional
    public AiProfileResponse revertSlot(Long userId, Long id, String slotKey) {
        AiProfile profile = owned(userId, id);
        SlotKey key;
        try {
            key = SlotKey.ofWire(slotKey);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown slot: " + slotKey);
        }
        SlotPrompt slot = profile.slot(key);
        if (slot != null) {
            slot.setText(slot.getOriginalText());
        }
        return AiProfileMapper.full(repository.save(profile));
    }

    @Override
    @Transactional
    public AiProfileResponse restore(Long userId, Long id) {
        AiProfile profile = owned(userId, id);
        catalog.restore(profile);
        return AiProfileMapper.full(repository.save(profile));
    }

    @Override
    @Transactional
    public void delete(Long userId, Long id) {
        AiProfile profile = owned(userId, id);
        if (repository.countByUserId(userId) <= 1) {
            throw new IllegalArgumentException("You need at least one AI Profile.");
        }
        // Sessions and quiz attempts that used it keep their history — the FK is ON DELETE SET NULL.
        repository.delete(profile);
    }

    private AiProfile owned(Long userId, Long id) {
        return repository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new NoSuchElementException("AI profile not found"));
    }

    private static Set<Capability> parseCapabilities(List<String> wire) {
        EnumSet<Capability> out = EnumSet.noneOf(Capability.class);
        for (String w : wire) {
            try {
                out.add(Capability.ofWire(w));
            } catch (IllegalArgumentException | NullPointerException ignored) {
                // skip anything that isn't one of the four
            }
        }
        return out;
    }

    private static EnumSet<Capability> copyOf(Set<Capability> capabilities) {
        EnumSet<Capability> out = EnumSet.noneOf(Capability.class);
        out.addAll(capabilities);
        return out;
    }
}
