package com.rental.sharing.user.dto;

import com.rental.sharing.user.entity.User;

import java.time.OffsetDateTime;

public record UserInfo(long id, String username, String nickname, OffsetDateTime createdAt) {
    public static UserInfo from(User user) {
        return new UserInfo(user.getId(), user.getUsername(), user.getNickname(), user.getCreatedAt());
    }
}
