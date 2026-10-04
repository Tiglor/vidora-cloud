package org.tiglor.content.enums;

import lombok.Getter;
import org.tiglor.common.core.BizException;
import org.tiglor.common.core.ResultCode;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * 审核对象类型，对应 {@code content_security_audit.target_type}。
 * <p>
 * 白名单而不是自由字符串：{@code uk_target} 靠 (target_type, target_id) 定位一条内容，
 * 写进去一个拼错的类型不会报错，只会让那条审核结果永远查不回来。
 * </p>
 */
@Getter
public enum AuditTargetType {

    VIDEO("video"),
    COMMENT("comment"),
    DANMAKU("danmaku");

    private final String code;

    AuditTargetType(String code) {
        this.code = code;
    }

    public static AuditTargetType of(String value) {
        if (value != null) {
            String normalized = value.trim();
            for (AuditTargetType type : values()) {
                if (type.code.equalsIgnoreCase(normalized)) {
                    return type;
                }
            }
        }
        throw new BizException(ResultCode.VALIDATE_FAILED, "未知的审核对象类型：" + value + "，可选 " + all());
    }

    private static String all() {
        return Arrays.stream(values()).map(AuditTargetType::getCode).collect(Collectors.joining(" / "));
    }
}
