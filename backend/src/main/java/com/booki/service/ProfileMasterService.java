package com.booki.service;

import com.booki.dto.CreateProfileMasterRequest;
import com.booki.dto.ProfileMasterResponse;

import java.util.List;

public interface ProfileMasterService {
    List<ProfileMasterResponse> listActive();
    ProfileMasterResponse create(CreateProfileMasterRequest request);
}
