package com.rental.sharing.common.exception;

import com.rental.sharing.common.api.ErrorCode;

/**
 * 可预期的业务失败，由全局异常处理器转换为统一响应。
 * 异常消息会直接返回客户端，应使用业务提示，避免包含 SQL、凭据等内部信息。
 */
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    /** 使用错误码中定义的默认提示。 */
    public BusinessException(ErrorCode errorCode) {
        this(errorCode, errorCode.getMessage());
    }

    /** 在保留错误码和 HTTP 状态的同时，提供适合当前业务场景的提示。 */
    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
