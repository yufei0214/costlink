package com.costlink.controller;

import com.costlink.config.UserPrincipal;
import com.costlink.dto.ApiResponse;
import com.costlink.dto.LoginRequest;
import com.costlink.dto.LoginResponse;
import com.costlink.entity.User;
import com.costlink.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = authService.login(request);
        return ApiResponse.success(response);
    }

    @GetMapping("/me")
    public ApiResponse<LoginResponse.UserInfo> getCurrentUser(@AuthenticationPrincipal UserPrincipal principal) {
        User user = authService.getCurrentUser(principal.getId());
        return ApiResponse.success(new LoginResponse.UserInfo(
            user.getId(),
            user.getUsername(),
            user.getDisplayName(),
            user.getRole(),
            user.getAlipayAccount()
        ));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout() {
        return ApiResponse.success("登出成功", null);
    }
}
