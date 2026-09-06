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
import java.util.Objects;

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
        User user = userRepository.findById(userId).orElseThrow();
        List<ReaderProfile> visible = repository.visibleTo(userId);
        Long defaultId = idOf(effectiveDefault(user, visible));
        return visible.stream().map(r -> toResponse(r, defaultId)).toList();
    }

    @Override
    @Transactional
    public ReaderProfileResponse create(Long userId, CreateReaderProfileRequest request) {
        User user = userRepository.findById(userId).orElseThrow();
        List<ReaderProfile> visible = repository.visibleTo(userId);
        boolean hadOwnProfile = visible.stream().anyMatch(r -> r.getUser() != null);

        ReaderProfile from = request.getFromId() != null
                ? visible.stream().filter(r -> r.getId().equals(request.getFromId())).findFirst().orElse(null)
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
        ReaderProfile saved = repository.save(profile);

        // The user's first own reader profile becomes their default — until then
        // sessions run on the built-in "General reader". Later profiles don't
        // take the slot; the user re-points it explicitly (update isDefault).
        if (!hadOwnProfile && user.getDefaultReaderProfile() == null) {
            user.setDefaultReaderProfile(saved);
            userRepository.save(user);
        }
        return toResponse(saved, idOf(effectiveDefault(user, repository.visibleTo(userId))));
    }

    @Override
    @Transactional
    public ReaderProfileResponse update(Long userId, Long id, UpdateReaderProfileRequest request) {
        User user = userRepository.findById(userId).orElseThrow();
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
        ReaderProfile saved = repository.save(profile);

        if (Boolean.TRUE.equals(request.getIsDefault())) {
            user.setDefaultReaderProfile(saved);
            userRepository.save(user);
        }
        return toResponse(saved, idOf(effectiveDefault(user, repository.visibleTo(userId))));
    }

    @Override
    @Transactional
    public void delete(Long userId, Long id) {
        User user = userRepository.findById(userId).orElseThrow();
        ReaderProfile profile = repository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new NoSuchElementException("Reader profile not found"));

        ReaderProfile userDefault = user.getDefaultReaderProfile();
        if (userDefault != null && userDefault.getId().equals(profile.getId())) {
            // Drop back to the built-in "General reader" (effectiveDefault falls through to it).
            user.setDefaultReaderProfile(null);
            userRepository.save(user);
        }
        // Sessions FK to reader_profiles is ON DELETE SET NULL — they fall back to the default at read time.
        repository.delete(profile);
    }

    @Override
    @Transactional(readOnly = true)
    public ReaderProfile resolveFor(Session session) {
        User user = userRepository.findById(session.getUser().getId()).orElseThrow();
        List<ReaderProfile> visible = repository.visibleTo(user.getId());
        if (session.getReaderProfile() != null) {
            ReaderProfile chosen = visible.stream()
                    .filter(r -> r.getId().equals(session.getReaderProfile().getId())).findFirst().orElse(null);
            if (chosen != null) {
                return chosen;
            }
        }
        return effectiveDefault(user, visible);
    }

    @Override
    @Transactional(readOnly = true)
    public ReaderProfile forNewSession(Long userId, Long requestedId) {
        User user = userRepository.findById(userId).orElseThrow();
        List<ReaderProfile> visible = repository.visibleTo(userId);
        if (requestedId != null) {
            ReaderProfile requested = visible.stream()
                    .filter(r -> r.getId().equals(requestedId)).findFirst().orElse(null);
            if (requested != null) {
                return requested;
            }
        }
        return effectiveDefault(user, visible);
    }

    /**
     * The reader profile in effect for {@code user}: their explicit default when
     * set and still visible, else the built-in "General reader", else the first
     * visible profile (or null when nothing is visible, e.g. in tests without the
     * seeded built-in).
     */
    private ReaderProfile effectiveDefault(User user, List<ReaderProfile> visible) {
        ReaderProfile explicit = user.getDefaultReaderProfile();
        if (explicit != null) {
            ReaderProfile owned = visible.stream()
                    .filter(r -> r.getId().equals(explicit.getId())).findFirst().orElse(null);
            if (owned != null) {
                return owned;
            }
        }
        return visible.stream().filter(ReaderProfile::isDefaultProfile).findFirst()
                .orElse(visible.isEmpty() ? null : visible.get(0));
    }

    private static Long idOf(ReaderProfile profile) {
        return profile != null ? profile.getId() : null;
    }

    private static ReaderProfileResponse toResponse(ReaderProfile r, Long defaultId) {
        return new ReaderProfileResponse(
                r.getId(),
                r.getName(),
                Objects.equals(r.getId(), defaultId),
                r.isReadOnly(),
                r.getReaderLevel() != null ? r.getReaderLevel().wire() : null,
                r.getContext(),
                r.getUpdatedAt());
    }
}
