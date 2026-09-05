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
import com.booki.service.ReaderProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class ReaderProfileServiceImpl implements ReaderProfileService {

    static final String GENERIC_CONTEXT =
            "My goal for this reading: \n"
                    + "What I already know about the topic: \n"
                    + "How I like to learn (examples, definitions, pace): \n"
                    + "Anything that helps me (short paragraphs, dyslexia-friendly formatting, ...): ";

    private final ReaderProfileRepository repository;
    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public List<ReaderProfileResponse> list(Long userId) {
        return repository.visibleTo(userId).stream().map(ReaderProfileServiceImpl::toResponse).toList();
    }

    @Override
    @Transactional
    public ReaderProfileResponse create(Long userId, CreateReaderProfileRequest request) {
        User user = userRepository.findById(userId).orElseThrow();
        ReaderProfile from = request.getFromId() != null
                ? repository.visibleTo(userId).stream()
                        .filter(r -> r.getId().equals(request.getFromId())).findFirst().orElse(null)
                : null;

        ReaderProfile profile = new ReaderProfile();
        profile.setUser(user);
        profile.setName(request.getName().trim());
        profile.setContext(request.getContext() != null
                ? request.getContext()
                : from != null ? from.getContext() : GENERIC_CONTEXT);
        profile.setReaderLevel(request.getReaderLevel() != null
                ? ReaderLevel.ofWire(request.getReaderLevel())
                : from != null ? from.getReaderLevel() : null);
        profile.setDefaultProfile(false);
        profile.setReadOnly(false);
        return toResponse(repository.save(profile));
    }

    @Override
    @Transactional
    public ReaderProfileResponse update(Long userId, Long id, UpdateReaderProfileRequest request) {
        ReaderProfile profile = repository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new NoSuchElementException(
                        "Reader profile not found (the built-in default is not editable)"));

        if (request.getName() != null && !request.getName().isBlank()) {
            profile.setName(request.getName().trim());
        }
        if (request.getContext() != null) {
            profile.setContext(request.getContext());
        }
        if (request.getReaderLevel() != null) {
            profile.setReaderLevel(ReaderLevel.ofWire(request.getReaderLevel()));
        }
        if (Boolean.TRUE.equals(request.getIsDefault())) {
            repository.visibleTo(userId).forEach(r -> {
                r.setDefaultProfile(r.getId().equals(profile.getId()));
                repository.save(r);
            });
        }
        return toResponse(repository.save(profile));
    }

    @Override
    @Transactional
    public void delete(Long userId, Long id) {
        ReaderProfile profile = repository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new NoSuchElementException("Reader profile not found"));
        boolean wasDefault = profile.isDefaultProfile();
        // Sessions FK to reader_profiles is ON DELETE SET NULL — they fall back to the default at read time.
        repository.delete(profile);
        if (wasDefault) {
            repository.findFirstByUserIsNull().ifPresent(builtIn -> {
                builtIn.setDefaultProfile(true);
                repository.save(builtIn);
            });
        }
    }

    @Override
    @Transactional(readOnly = true)
    public ReaderProfile resolveFor(Session session) {
        Long userId = session.getUser().getId();
        List<ReaderProfile> visible = repository.visibleTo(userId);
        if (session.getReaderProfile() != null) {
            ReaderProfile chosen = visible.stream()
                    .filter(r -> r.getId().equals(session.getReaderProfile().getId())).findFirst().orElse(null);
            if (chosen != null) {
                return chosen;
            }
        }
        return visible.stream().filter(ReaderProfile::isDefaultProfile).findFirst()
                .orElse(visible.isEmpty() ? null : visible.get(0));
    }

    @Override
    @Transactional(readOnly = true)
    public ReaderProfile forNewSession(Long userId, Long requestedId) {
        List<ReaderProfile> visible = repository.visibleTo(userId);
        if (requestedId != null) {
            ReaderProfile requested = visible.stream()
                    .filter(r -> r.getId().equals(requestedId)).findFirst().orElse(null);
            if (requested != null) {
                return requested;
            }
        }
        return visible.stream().filter(ReaderProfile::isDefaultProfile).findFirst()
                .orElse(visible.isEmpty() ? null : visible.get(0));
    }

    private static ReaderProfileResponse toResponse(ReaderProfile r) {
        return new ReaderProfileResponse(
                r.getId(),
                r.getName(),
                r.isDefaultProfile(),
                r.isReadOnly(),
                r.getReaderLevel() != null ? r.getReaderLevel().wire() : null,
                r.getContext(),
                r.getUpdatedAt());
    }
}
