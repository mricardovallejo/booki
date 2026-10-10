package com.booki.service.impl;

import com.booki.domain.Capability;
import com.booki.domain.User;
import com.booki.dto.UpdateUserRequest;
import com.booki.dto.UserResponse;
import com.booki.repository.UserRepository;
import com.booki.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

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
        if (request.getEnabledCapabilities() != null) {
            user.setEnabledCapabilities(parseCapabilities(request.getEnabledCapabilities()));
        }

        userRepository.save(user);
        return toResponse(user);
    }

    private static Set<Capability> parseCapabilities(List<String> wire) {
        EnumSet<Capability> out = EnumSet.noneOf(Capability.class);
        for (String w : wire) {
            try {
                out.add(Capability.ofWire(w));
            } catch (IllegalArgumentException | NullPointerException ignored) {
                // skip anything that isn't one of the four
            }
        }
        return out;
    }

    private UserResponse toResponse(User user) {
        List<String> capabilities = user.getEnabledCapabilities().stream().sorted().map(Capability::wire).toList();
        return new UserResponse(user.getId(), user.getEmail(), user.getName(), user.getCreatedAt(), capabilities);
    }
}
