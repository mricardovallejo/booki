package com.booki.service.impl;

import com.booki.domain.User;
import com.booki.dto.AuthRequest;
import com.booki.dto.AuthResponse;
import com.booki.dto.UserResponse;
import com.booki.prompt.SlotPromptCatalog;
import com.booki.repository.AiProfileRepository;
import com.booki.repository.UserRepository;
import com.booki.security.JwtUtil;
import com.booki.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final AiProfileRepository aiProfileRepository;
    private final SlotPromptCatalog slotPromptCatalog;

    @Override
    public AuthResponse register(AuthRequest request) {
        String email = normalizeEmail(request.getEmail());
        if (userRepository.findByEmail(email).isPresent()) {
            throw new IllegalArgumentException("Email already registered");
        }
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setName(resolveName(request, email));
        userRepository.save(user);
        aiProfileRepository.saveAll(slotPromptCatalog.seedFor(user));
        return toAuthResponse(user);
    }

    @Override
    public AuthResponse login(AuthRequest request) {
        User user = userRepository.findByEmail(normalizeEmail(request.getEmail()))
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid credentials");
        }
        return toAuthResponse(user);
    }

    /** Emails are case-insensitive in practice; normalizing avoids duplicate accounts and login mismatches. */
    private String normalizeEmail(String email) {
        return email.trim().toLowerCase();
    }

    private String resolveName(AuthRequest request, String normalizedEmail) {
        if (request.getName() != null && !request.getName().isBlank()) {
            return request.getName().trim();
        }
        return normalizedEmail.split("@")[0];
    }

    private AuthResponse toAuthResponse(User user) {
        String token = jwtUtil.generateToken(user.getEmail(), user.getId());
        UserResponse userResponse = new UserResponse(
                user.getId(), user.getEmail(), user.getName(), user.getCreatedAt());
        return new AuthResponse(token, userResponse);
    }
}
