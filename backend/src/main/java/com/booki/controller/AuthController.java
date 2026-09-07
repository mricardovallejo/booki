package com.booki.controller;

import com.booki.dto.AuthRequest;
import com.booki.dto.AuthResponse;
import com.booki.service.AuthService;
import com.booki.service.impl.WelcomeDocumentProvisioner;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final WelcomeDocumentProvisioner welcomeDocumentProvisioner;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody AuthRequest request) {
        AuthResponse response = authService.register(request);
        // Fire-and-forget once the account is committed: @Async, best-effort, adds
        // nothing to the response time (ADR-022).
        welcomeDocumentProvisioner.provisionFor(response.getUser().getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody AuthRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }
}
