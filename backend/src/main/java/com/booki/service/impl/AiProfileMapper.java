package com.booki.service.impl;

import com.booki.domain.AiProfile;
import com.booki.domain.Capability;
import com.booki.domain.SlotKey;
import com.booki.domain.SlotPrompt;
import com.booki.dto.AiProfileResponse;
import com.booki.dto.AiProfileSlotResponse;
import com.booki.dto.AiProfileSummaryResponse;

import java.util.Comparator;
import java.util.List;

final class AiProfileMapper {

    private AiProfileMapper() {
    }

    static AiProfileSummaryResponse summary(AiProfile p) {
        return new AiProfileSummaryResponse(
                p.getId(), p.getName(), p.isDefaultProfile(), wireLevel(p), wireCapabilities(p), p.getUpdatedAt());
    }

    static AiProfileResponse full(AiProfile p) {
        List<AiProfileSlotResponse> slots = p.getSlots().stream()
                .sorted(Comparator.comparingInt(s -> s.getSlot().ordinal()))
                .map(AiProfileMapper::slot)
                .toList();
        return new AiProfileResponse(
                p.getId(), p.getName(), p.isDefaultProfile(), wireLevel(p), wireCapabilities(p), p.getUpdatedAt(), slots);
    }

    private static AiProfileSlotResponse slot(SlotPrompt sp) {
        SlotKey k = sp.getSlot();
        return new AiProfileSlotResponse(
                k.wire(), k.label(), k.group().wire(),
                k.lockedPreamble(), k.lockedPostamble(),
                sp.getText(), sp.getOriginalText(), sp.isModified());
    }

    private static String wireLevel(AiProfile p) {
        return p.getReaderLevel() != null ? p.getReaderLevel().wire() : null;
    }

    private static List<String> wireCapabilities(AiProfile p) {
        return p.getEnabledCapabilities().stream().sorted().map(Capability::wire).toList();
    }
}
