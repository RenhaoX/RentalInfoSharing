package com.rental.sharing.user.service;

import com.rental.sharing.auth.dto.CredentialsRequest;
import com.rental.sharing.common.api.ErrorCode;
import com.rental.sharing.common.exception.BusinessException;
import com.rental.sharing.user.dto.UserInfo;
import com.rental.sharing.user.entity.User;
import com.rental.sharing.user.mapper.UserMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final String missingUserHash;

    public UserService(UserMapper userMapper, PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        // 不存在的账号也执行 BCrypt，避免查询是否命中造成明显的耗时差异。
        this.missingUserHash = passwordEncoder.encode("unavailable-account-password");
    }

    @Transactional
    public UserInfo register(CredentialsRequest request) {
        User user = new User();
        user.setUsername(request.username());
        user.setNickname(request.username());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ErrorCode.USERNAME_CONFLICT);
        }
        return UserInfo.from(userMapper.selectById(user.getId()));
    }

    public User authenticate(String username, String password) {
        User user = userMapper.findByUsername(username);
        boolean passwordMatches = passwordEncoder.matches(password, user == null ? missingUserHash : user.getPasswordHash());
        if (user == null || !passwordMatches) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }
        ensureActive(user);
        return user;
    }

    public User requireActive(long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        ensureActive(user);
        return user;
    }

    public UserInfo me(long userId) {
        return UserInfo.from(requireActive(userId));
    }

    private void ensureActive(User user) {
        if (!Integer.valueOf(1).equals(user.getStatus())) {
            throw new BusinessException(ErrorCode.ACCOUNT_DISABLED);
        }
    }
}
