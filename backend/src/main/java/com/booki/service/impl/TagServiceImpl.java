package com.booki.service.impl;

import com.booki.domain.Document;
import com.booki.domain.Tag;
import com.booki.domain.User;
import com.booki.dto.CreateTagRequest;
import com.booki.dto.TagResponse;
import com.booki.dto.UpdateTagRequest;
import com.booki.repository.DocumentRepository;
import com.booki.repository.TagRepository;
import com.booki.repository.UserRepository;
import com.booki.service.TagService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class TagServiceImpl implements TagService {

    private final TagRepository tagRepository;
    private final DocumentRepository documentRepository;
    private final UserRepository userRepository;

    @Override
    public List<TagResponse> list(Long userId) {
        return tagRepository.findByUserId(userId).stream().map(this::toResponse).toList();
    }

    @Override
    public TagResponse create(Long userId, CreateTagRequest request) {
        User user = userRepository.findById(userId).orElseThrow();

        Tag tag = new Tag();
        tag.setUser(user);
        tag.setName(request.getName().trim());

        if (request.getDocumentIds() != null) {
            for (Long documentId : request.getDocumentIds()) {
                documentRepository.findByIdAndUserId(documentId, userId).ifPresent(tag.getDocuments()::add);
            }
        }

        tagRepository.save(tag);
        return toResponse(tag);
    }

    @Override
    public TagResponse rename(Long userId, Long tagId, UpdateTagRequest request) {
        Tag tag = findOwned(userId, tagId);
        if (request.getName() != null && !request.getName().isBlank()) {
            tag.setName(request.getName().trim());
        }
        tagRepository.save(tag);
        return toResponse(tag);
    }

    @Override
    public void delete(Long userId, Long tagId) {
        Tag tag = findOwned(userId, tagId);
        tagRepository.delete(tag);
    }

    @Override
    public TagResponse addDocument(Long userId, Long tagId, Long documentId) {
        Tag tag = findOwned(userId, tagId);
        Document document = documentRepository.findByIdAndUserId(documentId, userId)
                .orElseThrow(() -> new NoSuchElementException("Document not found"));
        tag.getDocuments().add(document);
        tagRepository.save(tag);
        return toResponse(tag);
    }

    @Override
    public TagResponse removeDocument(Long userId, Long tagId, Long documentId) {
        Tag tag = findOwned(userId, tagId);
        tag.getDocuments().removeIf(d -> d.getId().equals(documentId));
        tagRepository.save(tag);
        return toResponse(tag);
    }

    private Tag findOwned(Long userId, Long tagId) {
        return tagRepository.findByIdAndUserId(tagId, userId)
                .orElseThrow(() -> new NoSuchElementException("Tag not found"));
    }

    private TagResponse toResponse(Tag tag) {
        List<Long> documentIds = tag.getDocuments().stream().map(Document::getId).toList();
        return new TagResponse(tag.getId(), tag.getName(), documentIds, tag.getCreatedAt());
    }
}
