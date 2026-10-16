package com.rental.sharing.identity.api;

/** 已通过认证的请求身份，仅携带用户标识；账号有效性由 IdentityAccess 检查。 */
public record AuthenticatedUser(long userId) {}
