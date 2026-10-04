package org.tiglor.interact.enums;

import lombok.Getter;
import org.tiglor.common.core.BizException;
import org.tiglor.common.core.ResultCode;

import java.util.Locale;

/** 互动对象类型，对应 {@code interact_action.target_type}。 */
@Getter
public enum TargetType {

    VIDEO("video"),
    COMMENT("comment");

    private final String code;

    TargetType(String code) {
        this.code = code;
    }

    public static TargetType of(String code) {
        if (code != null) {
            String normalized = code.trim().toLowerCase(Locale.ROOT);
            for (TargetType type : values()) {
                if (type.code.equals(normalized)) {
                    return type;
                }
            }
        }
        throw new BizException(ResultCode.VALIDATE_FAILED, "未知的对象类型：" + code + "，可选 video / comment");
    }
}
