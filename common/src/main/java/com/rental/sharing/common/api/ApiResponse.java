package com.rental.sharing.common.api;

import org.slf4j.MDC;

/**
 * 接口统一响应，HTTP 状态由控制器或异常处理器设置。
 *
 * @param code    应用错误码，0 表示成功
 * @param message 可直接向用户展示的结果说明
 * @param data    业务数据，错误响应为 null
 * @param traceId 当前请求的追踪标识，用于关联响应与服务端日志
 * @param <T>     业务数据类型
 */
public record ApiResponse<T>(int code, String message, T data, String traceId) {

    public static <T> ApiResponse<T> success(T data) {
        // TraceIdFilter 已将当前请求的标识写入 MDC，响应复用该值。
        return new ApiResponse<>(0, "ok", data, MDC.get("traceId"));
    }

    /** 错误响应不携带业务数据；message 应使用安全的对外提示。 */
    public static ApiResponse<Void> error(ErrorCode errorCode, String message) {
        return new ApiResponse<>(errorCode.getCode(), message, null, MDC.get("traceId"));
    }
}
