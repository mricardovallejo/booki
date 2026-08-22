package com.booki.service;

import com.booki.dto.CreateTagRequest;
import com.booki.dto.TagResponse;
import com.booki.dto.UpdateTagRequest;

import java.util.List;

public interface TagService {
    List<TagResponse> list(Long userId);
    TagResponse create(Long userId, CreateTagRequest request);
    TagResponse rename(Long userId, Long tagId, UpdateTagRequest request);
    void delete(Long userId, Long tagId);
    TagResponse addDocument(Long userId, Long tagId, Long documentId);
    TagResponse removeDocument(Long userId, Long tagId, Long documentId);
}
