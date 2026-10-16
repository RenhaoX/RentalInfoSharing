package com.rental.sharing.auth.service;

import com.rental.sharing.auth.config.AuthProperties;
import com.rental.sharing.auth.dto.CredentialsRequest;
import com.rental.sharing.auth.dto.TokenResponse;
import com.rental.sharing.auth.entity.RefreshToken;
import com.rental.sharing.auth.mapper.RefreshTokenMapper;
import com.rental.sharing.common.api.ErrorCode;
import com.rental.sharing.common.exception.BusinessException;
import com.rental.sharing.user.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.ZoneOffset;

@Service
public class AuthService {
    private final UserService userService;
    private final RefreshTokenMapper tokenMapper;
    private final JwtService jwtService;
    private final AuthProperties properties;
    private final Clock clock;

    public AuthService(UserService userService, RefreshTokenMapper tokenMapper, JwtService jwtService,
                       AuthProperties properties, Clock clock) {
        this.userService = userService;
        this.tokenMapper = tokenMapper;
        this.jwtService = jwtService;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public TokenResponse login(CredentialsRequest request) {
        var user = userService.authenticate(request.username(), request.password());
        return issueTokens(user.getId());
    }

    @Transactional
    public TokenResponse refresh(String rawToken) {
        RefreshToken token = findToken(rawToken);
        userService.requireActive(token.getUserId());
        if (tokenMapper.consume(token.getId()) != 1) {
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID);
        }
        // 吊销与新令牌写入同事务；写入失败时回滚，旧令牌仍可重试。
        return issueTokens(token.getUserId());
    }

    @Transactional
    public void logout(long userId, String rawToken) {
        RefreshToken token = findToken(rawToken);
        if (token.getUserId() != userId) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        tokenMapper.revoke(token.getId(), userId);
    }

    private RefreshToken findToken(String rawToken) {
        RefreshToken token = tokenMapper.findByHash(RefreshTokenCodec.hash(rawToken));
        if (token == null) {
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID);
        }
        return token;
    }

    private TokenResponse issueTokens(long userId) {
        String rawToken = RefreshTokenCodec.generate();
        RefreshToken token = new RefreshToken();
        token.setUserId(userId);
        token.setTokenHash(RefreshTokenCodec.hash(rawToken));
        token.setExpiresAt(clock.instant().plus(properties.refreshTokenTtl()).atOffset(ZoneOffset.UTC));
        tokenMapper.insert(token);
        return new TokenResponse(jwtService.issue(userId), rawToken, "Bearer",
                properties.accessTokenTtl().toSeconds(), properties.refreshTokenTtl().toSeconds());
    }
}
