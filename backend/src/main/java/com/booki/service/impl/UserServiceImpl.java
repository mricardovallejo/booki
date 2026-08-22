package com.booki.service.impl;

import com.booki.domain.User;
import com.booki.dto.UpdateUserRequest;
import com.booki.dto.UserResponse;
import com.booki.repository.UserRepository;
import com.booki.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;

    @Override
    public UserResponse getCurrentUser(Long userId) {
        return toResponse(userRepository.findById(userId).orElseThrow());
    }

    @Override
    public UserResponse updateCurrentUser(Long userId, UpdateUserRequest request) {
        User user = userRepository.findById(userId).orElseThrow();

        if (request.getName() != null && !request.getName().isBlank()) {
            user.setName(request.getName().trim());
        }
        if (request.getBio() != null) {
            user.setBio(request.getBio().trim());
        }
        if (request.getSystemPrompt() != null) {
            user.setSystemPrompt(request.getSystemPrompt().trim());
        }

        userRepository.save(user);
        return toResponse(user);
    }

    private UserResponse toResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getName(),
                user.getBio(),
                user.getSystemPrompt(),
                user.getCreatedAt()
        );
    }
}
