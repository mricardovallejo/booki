package com.booki.controller;

import com.booki.dto.CreateProfileMasterRequest;
import com.booki.dto.ProfileMasterResponse;
import com.booki.dto.UpdateProfileMasterRequest;
import com.booki.service.ProfileMasterService;
import com.booki.util.SecurityUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/profile-masters")
@RequiredArgsConstructor
public class ProfileMasterController {

    private final ProfileMasterService profileMasterService;

    @GetMapping
    public ResponseEntity<List<ProfileMasterResponse>> list() {
        return ResponseEntity.ok(profileMasterService.list(SecurityUtil.currentUserId()));
    }

    @PostMapping
    public ResponseEntity<ProfileMasterResponse> create(@Valid @RequestBody CreateProfileMasterRequest request) {
        ProfileMasterResponse response = profileMasterService.create(SecurityUtil.currentUserId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ProfileMasterResponse> update(@PathVariable Long id,
                                                         @RequestBody UpdateProfileMasterRequest request) {
        return ResponseEntity.ok(profileMasterService.update(SecurityUtil.currentUserId(), id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        profileMasterService.delete(SecurityUtil.currentUserId(), id);
        return ResponseEntity.noContent().build();
    }
}
