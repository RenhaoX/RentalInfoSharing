package com.rental.sharing.user.controller;

import com.rental.sharing.auth.security.AuthenticatedUser;
import com.rental.sharing.common.api.ApiResponse;
import com.rental.sharing.user.dto.UserInfo;
import com.rental.sharing.user.service.UserService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {
    private final UserService userService;

    public UserController(UserService userService) { this.userService = userService; }

    @GetMapping("/me")
    public ApiResponse<UserInfo> me(@AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.success(userService.me(user.userId()));
    }
}
