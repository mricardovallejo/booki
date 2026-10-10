package com.booki.service;

import com.booki.domain.AppProfileSlotKey;
import com.booki.domain.User;
import com.booki.dto.AppProfileSlotResponse;
import com.booki.dto.UpdateAppProfileRequest;

import java.util.List;

/**
 * The account-wide App Profile: what each difficulty level means, how
 * BooKI's functions behave, and when it routes to one on its own — one
 * editable set per account, never per tutor profile.
 */
public interface AppProfileSlotService {

    List<AppProfileSlotResponse> list(Long userId);

    List<AppProfileSlotResponse> update(Long userId, UpdateAppProfileRequest request);

    List<AppProfileSlotResponse> revert(Long userId, String key);

    List<AppProfileSlotResponse> restore(Long userId);

    /** The active text for one slot — read by every function/routing/difficulty prompt call. Blank if none seeded yet. */
    String textFor(User user, AppProfileSlotKey key);
}
