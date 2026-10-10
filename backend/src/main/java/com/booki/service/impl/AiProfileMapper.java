package com.booki.service.impl;

import com.booki.domain.AiProfile;
import com.booki.dto.AiProfileResponse;
import com.booki.dto.AiProfileSummaryResponse;

final class AiProfileMapper {

    private AiProfileMapper() {
    }

    static AiProfileSummaryResponse summary(AiProfile p) {
        return new AiProfileSummaryResponse(p.getId(), p.getName(), p.isDefaultProfile(), p.getUpdatedAt());
    }

    static AiProfileResponse full(AiProfile p) {
        return new AiProfileResponse(
                p.getId(), p.getName(), p.isDefaultProfile(), p.getUpdatedAt(),
                p.getPersonaText(), p.getPersonaOriginalText(), p.isPersonaModified());
    }
}
