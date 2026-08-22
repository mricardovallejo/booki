package com.booki.controller;

import com.booki.dto.UpdateUserRequest;
import com.booki.dto.UserResponse;
import com.booki.service.UserService;
import com.booki.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public ResponseEntity<UserResponse> getCurrentUser() {
        return ResponseEntity.ok(userService.getCurrentUser(SecurityUtil.currentUserId()));
    }

    @PatchMapping("/me")
    public ResponseEntity<UserResponse> updateCurrentUser(@RequestBody UpdateUserRequest request) {
        return ResponseEntity.ok(userService.updateCurrentUser(SecurityUtil.currentUserId(), request));
    }
}
