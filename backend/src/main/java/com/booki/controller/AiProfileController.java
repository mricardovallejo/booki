package com.booki.controller;

import com.booki.dto.AiProfileResponse;
import com.booki.dto.AiProfileSummaryResponse;
import com.booki.dto.DuplicateAiProfileRequest;
import com.booki.dto.RevertSlotRequest;
import com.booki.dto.UpdateAiProfileRequest;
import com.booki.service.AiProfileService;
import com.booki.util.SecurityUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/ai-profiles")
@RequiredArgsConstructor
public class AiProfileController {

    private final AiProfileService aiProfileService;

    @GetMapping
    public ResponseEntity<List<AiProfileSummaryResponse>> list() {
        return ResponseEntity.ok(aiProfileService.list(SecurityUtil.currentUserId()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AiProfileResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(aiProfileService.get(SecurityUtil.currentUserId(), id));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<AiProfileResponse> update(@PathVariable Long id,
                                                    @RequestBody UpdateAiProfileRequest request) {
        return ResponseEntity.ok(aiProfileService.update(SecurityUtil.currentUserId(), id, request));
    }

    @PostMapping("/{id}/duplicate")
    public ResponseEntity<AiProfileResponse> duplicate(@PathVariable Long id,
                                                       @RequestBody(required = false) DuplicateAiProfileRequest request) {
        String name = request != null ? request.getName() : null;
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(aiProfileService.duplicate(SecurityUtil.currentUserId(), id, name));
    }

    @PostMapping("/{id}/revert")
    public ResponseEntity<AiProfileResponse> revert(@PathVariable Long id,
                                                    @Valid @RequestBody RevertSlotRequest request) {
        return ResponseEntity.ok(aiProfileService.revertSlot(SecurityUtil.currentUserId(), id, request.getKey()));
    }

    @PostMapping("/{id}/restore")
    public ResponseEntity<AiProfileResponse> restore(@PathVariable Long id) {
        return ResponseEntity.ok(aiProfileService.restore(SecurityUtil.currentUserId(), id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        aiProfileService.delete(SecurityUtil.currentUserId(), id);
        return ResponseEntity.noContent().build();
    }
}
