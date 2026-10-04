package org.tiglor.content.enums;

import lombok.Getter;
import org.tiglor.common.core.BizException;
import org.tiglor.common.core.ResultCode;

/**
 * 人工复核结论，对应 {@code content_security_audit.manual_result}。
 * <p>
 * 「未复核」用 NULL 表示而不是 0：DDL 上这一列本来就 {@code DEFAULT NULL}，
 * 再加一个 0 就有了两种「没结论」的写法，待审队列的 {@code IS NULL} 条件会漏掉一半。
 * </p>
 */
@Getter
public enum ManualResult {

    /** 放行：人工确认可以展示 */
    PASS(1),
    /** 拦截：人工确认不能展示 */
    BLOCK(2);

    private final int code;

    ManualResult(int code) {
        this.code = code;
    }

    public static ManualResult of(Integer code) {
        if (code != null) {
            for (ManualResult result : values()) {
                if (result.code == code) {
                    return result;
                }
            }
        }
        throw new BizException(ResultCode.VALIDATE_FAILED, "未知的复核结论：" + code + "，可选 1-放行 2-拦截");
    }
}
