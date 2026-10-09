package com.rental.sharing.common.exception;

import com.rental.sharing.common.api.ApiResponse;
import com.rental.sharing.common.api.ErrorCode;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.stream.Collectors;

/**
 * 统一处理控制器中的业务异常、参数校验失败与未预期异常。
 * 继承 Spring MVC 的异常处理器，将框架请求错误也转换为 ApiResponse。
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 业务异常使用自身定义的 HTTP 状态和对外提示。 */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException exception) {
        return ResponseEntity.status(exception.getErrorCode().getHttpStatus())
                .body(ApiResponse.error(exception.getErrorCode(), exception.getMessage()));
    }

    /** 参数约束失败时返回通用提示，不直接暴露异常中的校验值或内部路径。 */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(ConstraintViolationException exception) {
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(ErrorCode.BAD_REQUEST, ErrorCode.BAD_REQUEST.getMessage()));
    }

    /** 汇总 @Valid 请求体的校验提示，去重后返回，不附带原始字段值。 */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        String message = exception.getBindingResult().getAllErrors().stream()
                .map(error -> error.getDefaultMessage() == null ? "参数不合法" : error.getDefaultMessage())
                .distinct()
                .collect(Collectors.joining("；"));
        return new ResponseEntity<>(ApiResponse.error(ErrorCode.BAD_REQUEST, message), headers, status);
    }

    /** 保留框架错误的 HTTP 状态及响应头，统一替换默认错误响应体。 */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception exception, Object body, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        if (status.is5xxServerError()) {
            log.error("请求处理失败", exception);
        }
        ErrorCode errorCode = ErrorCode.fromHttpStatus(status.value());
        return new ResponseEntity<>(ApiResponse.error(errorCode, errorCode.getMessage()), headers, status);
    }

    /** 未预期异常的详情只记录到日志，客户端收到通用 HTTP 500 提示。 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpectedException(Exception exception) {
        log.error("未处理的请求异常", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.getMessage()));
    }
}
