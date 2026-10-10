package com.booki.service.impl;

import com.booki.domain.Capability;
import com.booki.domain.User;
import com.booki.dto.UpdateUserRequest;
import com.booki.dto.UserResponse;
import com.booki.email.EmailList;
import com.booki.email.ReportRecipientResolver;
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
    private final ReportRecipientResolver reportRecipientResolver;

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
        if (request.getDefaultReportEmail() != null) {
            String trimmed = request.getDefaultReportEmail().trim();
            if (trimmed.isEmpty()) {
                user.setDefaultReportEmail(null);
            } else if (EmailList.isValid(trimmed)) {
                user.setDefaultReportEmail(EmailList.normalize(trimmed));
            } else {
                throw new IllegalArgumentException(
                        "defaultReportEmail must be one or more valid addresses separated by ';'");
            }
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
        return new UserResponse(user.getId(), user.getEmail(), user.getName(), user.getCreatedAt(), capabilities,
                user.getDefaultReportEmail(), reportRecipientResolver.resolveDefault(user));
    }
}
