package com.booki.controller;

import com.booki.dto.CreateReaderProfileRequest;
import com.booki.dto.ReaderProfileResponse;
import com.booki.dto.UpdateReaderProfileRequest;
import com.booki.service.ReaderProfileService;
import com.booki.util.SecurityUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/reader-profiles")
@RequiredArgsConstructor
public class ReaderProfileController {

    private final ReaderProfileService readerProfileService;

    @GetMapping
    public ResponseEntity<List<ReaderProfileResponse>> list() {
        return ResponseEntity.ok(readerProfileService.list(SecurityUtil.currentUserId()));
    }

    @PostMapping
    public ResponseEntity<ReaderProfileResponse> create(@Valid @RequestBody CreateReaderProfileRequest request) {
        ReaderProfileResponse response = readerProfileService.create(SecurityUtil.currentUserId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ReaderProfileResponse> update(@PathVariable Long id,
                                                        @Valid @RequestBody UpdateReaderProfileRequest request) {
        return ResponseEntity.ok(readerProfileService.update(SecurityUtil.currentUserId(), id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        readerProfileService.delete(SecurityUtil.currentUserId(), id);
        return ResponseEntity.noContent().build();
    }
}
