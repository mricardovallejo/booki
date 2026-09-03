package com.booki.service;

import com.booki.dto.AiProfileResponse;
import com.booki.dto.AiProfileSummaryResponse;
import com.booki.dto.UpdateAiProfileRequest;

import java.util.List;

public interface AiProfileService {

    List<AiProfileSummaryResponse> list(Long userId);

    AiProfileResponse get(Long userId, Long id);

    AiProfileResponse update(Long userId, Long id, UpdateAiProfileRequest request);

    AiProfileResponse duplicate(Long userId, Long id, String name);

    AiProfileResponse revertSlot(Long userId, Long id, String slotKey);

    AiProfileResponse restore(Long userId, Long id);

    void delete(Long userId, Long id);
}
