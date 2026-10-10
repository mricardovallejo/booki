package com.booki.service.impl;

import com.booki.domain.AiProfile;
import com.booki.dto.AiProfileResponse;
import com.booki.dto.AiProfileSummaryResponse;
import com.booki.dto.UpdateAiProfileRequest;
import com.booki.prompt.SlotPromptCatalog;
import com.booki.repository.AiProfileRepository;
import com.booki.service.AiProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;

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
        if (request.getPersona() != null) {
            profile.setPersonaText(request.getPersona());
        }
        if (Boolean.TRUE.equals(request.getIsDefault()) && !profile.isDefaultProfile()) {
            repository.findFirstByUserIdAndDefaultProfileTrueOrderByIdAsc(userId)
                    .ifPresent(old -> {
                        old.setDefaultProfile(false);
                        repository.save(old);
                    });
            profile.setDefaultProfile(true);
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
        copy.setPersonaText(source.getPersonaText());
        copy.setPersonaOriginalText(source.getPersonaOriginalText());
        return AiProfileMapper.full(repository.save(copy));
    }

    @Override
    @Transactional
    public AiProfileResponse revertPersona(Long userId, Long id) {
        AiProfile profile = owned(userId, id);
        profile.setPersonaText(profile.getPersonaOriginalText());
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
        boolean wasDefault = profile.isDefaultProfile();
        // Sessions and quiz attempts that used it keep their history — the FK is ON DELETE SET NULL.
        repository.delete(profile);
        if (wasDefault) {
            repository.findByUserIdOrderByIdAsc(userId).stream().findFirst()
                    .ifPresent(next -> {
                        next.setDefaultProfile(true);
                        repository.save(next);
                    });
        }
    }

    private AiProfile owned(Long userId, Long id) {
        return repository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new NoSuchElementException("AI profile not found"));
    }
}
