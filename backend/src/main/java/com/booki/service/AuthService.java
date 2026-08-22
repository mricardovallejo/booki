package com.booki.service;

import com.booki.dto.AuthRequest;
import com.booki.dto.AuthResponse;

public interface AuthService {
    AuthResponse register(AuthRequest request);
    AuthResponse login(AuthRequest request);
}
