package com.booki.controller;

import com.booki.dto.CreateTagRequest;
import com.booki.dto.TagResponse;
import com.booki.dto.UpdateTagRequest;
import com.booki.service.TagService;
import com.booki.util.SecurityUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Mounted at /api/collections for historical reasons — the product concept
 * is "Tags" (see docs/openapi.yaml); a document can belong to any number.
 */
@RestController
@RequestMapping("/api/collections")
@RequiredArgsConstructor
public class TagController {

    private final TagService tagService;

    @GetMapping
    public ResponseEntity<List<TagResponse>> list() {
        return ResponseEntity.ok(tagService.list(SecurityUtil.currentUserId()));
    }

    @PostMapping
    public ResponseEntity<TagResponse> create(@Valid @RequestBody CreateTagRequest request) {
        TagResponse response = tagService.create(SecurityUtil.currentUserId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PatchMapping("/{id}")
    public ResponseEntity<TagResponse> rename(@PathVariable Long id, @RequestBody UpdateTagRequest request) {
        return ResponseEntity.ok(tagService.rename(SecurityUtil.currentUserId(), id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        tagService.delete(SecurityUtil.currentUserId(), id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/documents/{documentId}")
    public ResponseEntity<TagResponse> addDocument(@PathVariable Long id, @PathVariable Long documentId) {
        return ResponseEntity.ok(tagService.addDocument(SecurityUtil.currentUserId(), id, documentId));
    }

    @DeleteMapping("/{id}/documents/{documentId}")
    public ResponseEntity<TagResponse> removeDocument(@PathVariable Long id, @PathVariable Long documentId) {
        return ResponseEntity.ok(tagService.removeDocument(SecurityUtil.currentUserId(), id, documentId));
    }
}
