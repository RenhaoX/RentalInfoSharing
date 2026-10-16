package com.rental.sharing.identity.api;

/** 身份模块向其他业务模块提供的账号访问契约，不暴露用户持久化模型。 */
public interface IdentityAccess {

    /** 账号不存在或已禁用时，抛出对应的业务异常。 */
    void requireActive(long userId);
}
