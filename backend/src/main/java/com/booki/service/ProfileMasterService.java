package com.booki.service;

import com.booki.dto.CreateProfileMasterRequest;
import com.booki.dto.ProfileMasterResponse;
import com.booki.dto.UpdateProfileMasterRequest;

import java.util.List;

public interface ProfileMasterService {
    List<ProfileMasterResponse> list(Long userId);
    ProfileMasterResponse create(Long userId, CreateProfileMasterRequest request);
    ProfileMasterResponse update(Long userId, Long id, UpdateProfileMasterRequest request);
    void delete(Long userId, Long id);

    /** Copies the seed templates (user_id IS NULL) into a newly registered user's own set. */
    void seedDefaultsForNewUser(Long userId);
}
