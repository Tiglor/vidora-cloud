package org.tiglor.common.core;

import lombok.Getter;

/**
 * 统一结果状态码
 */
@Getter
public enum ResultCode {

    SUCCESS(200, "success"),
    FAIL(500, "服务异常"),
    VALIDATE_FAILED(400, "参数校验失败"),
    UNAUTHORIZED(401, "未登录或Token失效"),
    FORBIDDEN(403, "无权限访问"),
    NOT_FOUND(404, "资源不存在"),
    TOO_MANY_REQUESTS(429, "请求过于频繁");

    private final Integer code;
    private final String message;

    ResultCode(Integer code, String message) {
        this.code = code;
        this.message = message;
    }
}
