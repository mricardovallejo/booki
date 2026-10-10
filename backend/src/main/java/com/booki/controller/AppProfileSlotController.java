package com.booki.controller;

import com.booki.dto.AppProfileSlotResponse;
import com.booki.dto.UpdateAppProfileRequest;
import com.booki.service.AppProfileSlotService;
import com.booki.util.SecurityUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/app-profile")
@RequiredArgsConstructor
public class AppProfileSlotController {

    private final AppProfileSlotService appProfileSlotService;

    @GetMapping
    public ResponseEntity<List<AppProfileSlotResponse>> list() {
        return ResponseEntity.ok(appProfileSlotService.list(SecurityUtil.currentUserId()));
    }

    @PutMapping
    public ResponseEntity<List<AppProfileSlotResponse>> update(@Valid @RequestBody UpdateAppProfileRequest request) {
        return ResponseEntity.ok(appProfileSlotService.update(SecurityUtil.currentUserId(), request));
    }

    @PostMapping("/{key}/revert")
    public ResponseEntity<List<AppProfileSlotResponse>> revert(@PathVariable String key) {
        return ResponseEntity.ok(appProfileSlotService.revert(SecurityUtil.currentUserId(), key));
    }

    @PostMapping("/restore")
    public ResponseEntity<List<AppProfileSlotResponse>> restore() {
        return ResponseEntity.ok(appProfileSlotService.restore(SecurityUtil.currentUserId()));
    }
}
