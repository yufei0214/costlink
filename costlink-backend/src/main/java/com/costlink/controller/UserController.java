package com.costlink.controller;

import com.costlink.config.UserPrincipal;
import com.costlink.dto.ApiResponse;
import com.costlink.entity.User;
import com.costlink.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/profile")
    public ApiResponse<User> getProfile(@AuthenticationPrincipal UserPrincipal principal) {
        User user = userService.getUserById(principal.getId());
        user.setPassword(null);
        return ApiResponse.success(user);
    }

    @PutMapping("/alipay")
    public ApiResponse<User> updateAlipayAccount(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody Map<String, String> body) {
        String alipayAccount = body.get("alipayAccount");
        User user = userService.updateAlipayAccount(principal.getId(), alipayAccount);
        user.setPassword(null);
        return ApiResponse.success("支付宝账号更新成功", user);
    }

    @PutMapping("/department")
    public ApiResponse<User> updateDepartment(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody Map<String, String> body) {
        String department = body.get("department");
        User user = userService.updateDepartment(principal.getId(), department);
        user.setPassword(null);
        return ApiResponse.success("所属组更新成功", user);
    }
}
