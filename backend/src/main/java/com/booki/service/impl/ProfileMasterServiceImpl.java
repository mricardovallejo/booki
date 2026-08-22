package com.booki.service.impl;

import com.booki.domain.ProfileMaster;
import com.booki.domain.User;
import com.booki.dto.CreateProfileMasterRequest;
import com.booki.dto.ProfileMasterResponse;
import com.booki.dto.UpdateProfileMasterRequest;
import com.booki.repository.ProfileMasterRepository;
import com.booki.repository.QuizAttemptRepository;
import com.booki.repository.SessionRepository;
import com.booki.repository.UserRepository;
import com.booki.service.ProfileMasterService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class ProfileMasterServiceImpl implements ProfileMasterService {

    private final ProfileMasterRepository profileMasterRepository;
    private final UserRepository userRepository;
    private final SessionRepository sessionRepository;
    private final QuizAttemptRepository quizAttemptRepository;

    @Override
    public List<ProfileMasterResponse> list(Long userId) {
        return profileMasterRepository.findByUserIdOrderByIdAsc(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public ProfileMasterResponse create(Long userId, CreateProfileMasterRequest request) {
        User user = userRepository.findById(userId).orElseThrow();

        ProfileMaster master = new ProfileMaster();
        master.setUser(user);
        master.setName(request.getName().trim());
        master.setDescription(request.getDescription() == null ? "" : request.getDescription().trim());
        master.setSystemPrompt(request.getSystemPrompt().trim());
        master.setConfigJson("{}");
        master.setIsActive(true);
        profileMasterRepository.save(master);
        return toResponse(master);
    }

    @Override
    public ProfileMasterResponse update(Long userId, Long id, UpdateProfileMasterRequest request) {
        ProfileMaster master = findOwned(userId, id);

        if (request.getName() != null && !request.getName().isBlank()) {
            master.setName(request.getName().trim());
        }
        if (request.getDescription() != null) {
            master.setDescription(request.getDescription().trim());
        }
        if (request.getSystemPrompt() != null && !request.getSystemPrompt().isBlank()) {
            master.setSystemPrompt(request.getSystemPrompt().trim());
        }

        profileMasterRepository.save(master);
        return toResponse(master);
    }

    @Override
    @Transactional
    public void delete(Long userId, Long id) {
        ProfileMaster master = findOwned(userId, id);
        // Past sessions/quiz attempts keep their history; they just lose the persona tag.
        sessionRepository.clearProfileMaster(id);
        quizAttemptRepository.clearProfileMaster(id);
        profileMasterRepository.delete(master);
    }

    @Override
    public void seedDefaultsForNewUser(Long userId) {
        User user = userRepository.findById(userId).orElseThrow();
        List<ProfileMaster> templates = profileMasterRepository.findByUserIdIsNull();

        for (ProfileMaster template : templates) {
            ProfileMaster copy = new ProfileMaster();
            copy.setUser(user);
            copy.setName(template.getName());
            copy.setDescription(template.getDescription());
            copy.setSystemPrompt(template.getSystemPrompt());
            copy.setConfigJson(template.getConfigJson());
            copy.setIsActive(true);
            profileMasterRepository.save(copy);
        }
    }

    private ProfileMaster findOwned(Long userId, Long id) {
        return profileMasterRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new NoSuchElementException("Profile Master not found"));
    }

    private ProfileMasterResponse toResponse(ProfileMaster master) {
        return new ProfileMasterResponse(
                master.getId(),
                master.getName(),
                master.getDescription(),
                master.getSystemPrompt()
        );
    }
}
