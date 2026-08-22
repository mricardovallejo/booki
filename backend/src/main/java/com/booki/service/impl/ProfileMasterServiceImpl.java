package com.booki.service.impl;

import com.booki.domain.ProfileMaster;
import com.booki.dto.CreateProfileMasterRequest;
import com.booki.dto.ProfileMasterResponse;
import com.booki.repository.ProfileMasterRepository;
import com.booki.service.ProfileMasterService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProfileMasterServiceImpl implements ProfileMasterService {

    private final ProfileMasterRepository profileMasterRepository;

    @Override
    public List<ProfileMasterResponse> listActive() {
        return profileMasterRepository.findByIsActiveTrue().stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public ProfileMasterResponse create(CreateProfileMasterRequest request) {
        ProfileMaster master = new ProfileMaster();
        master.setName(request.getName().trim());
        master.setDescription(request.getDescription() == null ? "" : request.getDescription().trim());
        master.setSystemPrompt(request.getSystemPrompt().trim());
        master.setConfigJson("{}");
        master.setIsActive(true);
        profileMasterRepository.save(master);
        return toResponse(master);
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
