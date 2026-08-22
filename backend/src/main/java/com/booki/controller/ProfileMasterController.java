package com.booki.controller;

import com.booki.dto.CreateProfileMasterRequest;
import com.booki.dto.ProfileMasterResponse;
import com.booki.service.ProfileMasterService;
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
        return ResponseEntity.ok(profileMasterService.listActive());
    }

    @PostMapping
    public ResponseEntity<ProfileMasterResponse> create(@Valid @RequestBody CreateProfileMasterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(profileMasterService.create(request));
    }
}
