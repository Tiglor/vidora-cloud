package org.tiglor.content.enums;

import lombok.Getter;
import org.tiglor.common.core.BizException;
import org.tiglor.common.core.ResultCode;

/**
 * 机审风险等级，对应 {@code content_security_audit.risk_level}。
 * <p>
 * {@link #MEDIUM} 是「进人工队列」的门槛：低风险的量太大，全推给人工等于没有机审；
 * 高风险的又必须拦住等复核。门槛放在这里而不是散在各处写死 {@code >= 2}，
 * 是为了调整策略时只改一个地方。
 * </p>
 */
@Getter
public enum RiskLevel {

    NONE(0, "无风险"),
    LOW(1, "低风险"),
    MEDIUM(2, "中风险"),
    HIGH(3, "高风险");

    private final int code;
    private final String label;

    RiskLevel(int code, String label) {
        this.code = code;
        this.label = label;
    }

    public static RiskLevel of(Integer code) {
        if (code != null) {
            for (RiskLevel level : values()) {
                if (level.code == code) {
                    return level;
                }
            }
        }
        throw new BizException(ResultCode.VALIDATE_FAILED, "未知的风险等级：" + code + "，可选 0-无 1-低 2-中 3-高");
    }

    /** 是否需要人工复核 */
    public boolean needsManualReview() {
        return code >= MEDIUM.code;
    }
}
