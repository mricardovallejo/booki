package com.booki.service.impl;

import com.booki.domain.Capability;
import com.booki.domain.User;
import com.booki.dto.AuthRequest;
import com.booki.dto.AuthResponse;
import com.booki.dto.UserResponse;
import com.booki.prompt.SlotPromptCatalog;
import com.booki.repository.AiProfileRepository;
import com.booki.repository.AppProfileSlotRepository;
import com.booki.repository.UserRepository;
import com.booki.security.JwtUtil;
import com.booki.service.AuthService;
import com.booki.service.event.UserRegisteredEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final AiProfileRepository aiProfileRepository;
    private final AppProfileSlotRepository appProfileSlotRepository;
    private final SlotPromptCatalog slotPromptCatalog;
    private final ReaderProfileProvisioner readerProfileProvisioner;
    private final ApplicationEventPublisher events;

    @Override
    @Transactional
    public AuthResponse register(AuthRequest request) {
        String email = normalizeEmail(request.getEmail());
        if (userRepository.findByEmail(email).isPresent()) {
            throw new IllegalArgumentException("Email already registered");
        }
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setName(resolveName(request, email));
        user.setEnabledCapabilities(slotPromptCatalog.defaultCapabilities());
        userRepository.save(user);
        aiProfileRepository.saveAll(slotPromptCatalog.seedFor(user));
        appProfileSlotRepository.saveAll(slotPromptCatalog.newAppProfileSlots(user));
        readerProfileProvisioner.provisionFor(user);
        // Post-registration side effects run after this transaction commits.
        events.publishEvent(new UserRegisteredEvent(user.getId()));
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
        List<String> capabilities = user.getEnabledCapabilities().stream().sorted().map(Capability::wire).toList();
        UserResponse userResponse = new UserResponse(
                user.getId(), user.getEmail(), user.getName(), user.getCreatedAt(), capabilities);
        return new AuthResponse(token, userResponse);
    }
}
