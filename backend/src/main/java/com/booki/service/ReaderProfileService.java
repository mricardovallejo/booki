package com.booki.service;

import com.booki.domain.ReaderProfile;
import com.booki.domain.Session;
import com.booki.dto.CreateReaderProfileRequest;
import com.booki.dto.ReaderProfileResponse;
import com.booki.dto.UpdateReaderProfileRequest;

import java.util.List;

public interface ReaderProfileService {

    List<ReaderProfileResponse> list(Long userId);

    ReaderProfileResponse create(Long userId, CreateReaderProfileRequest request);

    ReaderProfileResponse update(Long userId, Long id, UpdateReaderProfileRequest request);

    void delete(Long userId, Long id);

    /** The reader profile in effect for a session: its own, else the owner's default, else the built-in. */
    ReaderProfile resolveFor(Session session);

    /** Validate + return an owned/visible reader profile to attach to a new session, or null. */
    ReaderProfile forNewSession(Long userId, Long requestedId);
}
