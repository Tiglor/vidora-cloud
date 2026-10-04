package org.tiglor.common.core;

import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理：统一返回 ApiResult。
 * <p>
 * HTTP 状态码与业务码保持一致，网关、监控与前端才能按状态码识别错误；
 * 此前 BizException 一律返回 200，导致所有业务失败在监控里都是「成功」。
 * </p>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public ResponseEntity<ApiResult<Void>> handleBiz(BizException e) {
        HttpStatus status = resolveStatus(e.getCode());
        if (status.is5xxServerError()) {
            log.error("业务异常: code={}, message={}", e.getCode(), e.getMessage(), e);
        } else {
            log.warn("业务异常: code={}, message={}", e.getCode(), e.getMessage());
        }
        return ResponseEntity.status(status).body(ApiResult.error(e.getCode(), e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResult<Void>> handleValid(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldError() != null
                ? e.getBindingResult().getFieldError().getDefaultMessage()
                : ResultCode.VALIDATE_FAILED.getMessage();
        return badRequest(msg);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResult<Void>> handleConstraint(ConstraintViolationException e) {
        return badRequest(e.getMessage());
    }

    /** @PreAuthorize 等方法级鉴权失败应返回 403，而不是落到兜底分支变成 500 */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResult<Void>> handleAccessDenied(AccessDeniedException e) {
        log.warn("拒绝访问: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResult.error(ResultCode.FORBIDDEN.getCode(), ResultCode.FORBIDDEN.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResult<Void>> handleOther(Exception e) {
        log.error("未处理异常", e);
        // 不把内部异常信息透给调用方，避免泄露实现细节
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResult.error(ResultCode.FAIL.getCode(), ResultCode.FAIL.getMessage()));
    }

    private ResponseEntity<ApiResult<Void>> badRequest(String message) {
        log.warn("参数校验失败: {}", message);
        return ResponseEntity.badRequest()
                .body(ApiResult.error(ResultCode.VALIDATE_FAILED.getCode(), message));
    }

    /** ResultCode 的取值与 HTTP 状态码同名，可直接复用；无法识别的码归为 500 */
    private HttpStatus resolveStatus(Integer code) {
        HttpStatus status = code == null ? null : HttpStatus.resolve(code);
        return status == null ? HttpStatus.INTERNAL_SERVER_ERROR : status;
    }
}
