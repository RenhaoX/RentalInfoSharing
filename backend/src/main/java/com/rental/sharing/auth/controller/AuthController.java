package com.rental.sharing.auth.controller;

import com.rental.sharing.auth.dto.CredentialsRequest;
import com.rental.sharing.auth.dto.RefreshTokenRequest;
import com.rental.sharing.auth.dto.TokenResponse;
import com.rental.sharing.auth.security.AuthenticatedUser;
import com.rental.sharing.auth.service.AuthService;
import com.rental.sharing.common.api.ApiResponse;
import com.rental.sharing.user.dto.UserInfo;
import com.rental.sharing.user.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final UserService userService;

    public AuthController(AuthService authService, UserService userService) {
        this.authService = authService;
        this.userService = userService;
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<UserInfo>> register(
            @Valid @RequestBody CredentialsRequest request) {
        UserInfo registeredUser = userService.register(request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(registeredUser));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<TokenResponse>> login(
            @Valid @RequestBody CredentialsRequest request) {
        TokenResponse tokens = authService.login(request);

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(ApiResponse.success(tokens));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<TokenResponse>> refresh(
            @Valid @RequestBody RefreshTokenRequest request) {
        TokenResponse refreshedTokens = authService.refresh(request.refreshToken());

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(ApiResponse.success(refreshedTokens));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody RefreshTokenRequest request) {
        authService.logout(user.userId(), request.refreshToken());

        return ApiResponse.success(null);
    }
}
