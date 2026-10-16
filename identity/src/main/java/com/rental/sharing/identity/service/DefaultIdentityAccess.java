package com.rental.sharing.identity.service;

import com.rental.sharing.identity.api.IdentityAccess;
import com.rental.sharing.user.service.UserService;
import org.springframework.stereotype.Service;

@Service
public class DefaultIdentityAccess implements IdentityAccess {
    private final UserService userService;

    public DefaultIdentityAccess(UserService userService) {
        this.userService = userService;
    }

    @Override
    public void requireActive(long userId) {
        userService.requireActive(userId);
    }
}
