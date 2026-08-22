package com.booki.service;

import com.booki.dto.UpdateUserRequest;
import com.booki.dto.UserResponse;

public interface UserService {
    UserResponse getCurrentUser(Long userId);
    UserResponse updateCurrentUser(Long userId, UpdateUserRequest request);
}
