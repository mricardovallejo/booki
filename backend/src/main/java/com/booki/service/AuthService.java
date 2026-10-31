package com.booki.service;

import com.booki.dto.AuthRequest;
import com.booki.dto.AuthResponse;
import com.booki.dto.ChangePasswordRequest;
import com.booki.dto.ForgotPasswordRequest;
import com.booki.dto.ResetPasswordRequest;

public interface AuthService {
    AuthResponse register(AuthRequest request);
    AuthResponse login(AuthRequest request);
    void changePassword(Long userId, ChangePasswordRequest request);

    /** Behaves the same to the caller whether or not a link was actually sent. */
    void forgotPassword(ForgotPasswordRequest request);
    void resetPassword(ResetPasswordRequest request);
}
