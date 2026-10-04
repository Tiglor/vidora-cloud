package org.tiglor.recommend.enums;

import lombok.Getter;
import org.tiglor.common.core.BizException;
import org.tiglor.common.core.ResultCode;

import java.util.Locale;

/**
 * 产出这批推荐的算法，对应 {@code recommend_result.algo_type}。
 * <p>
 * 记这一列不是为了展示，是为了事后归因：同一个场景可以挂多路召回，
 * 出问题时得能回答「是协同过滤这一路挂了还是热度兜底那一路挂了」。
 * </p>
 */
@Getter
public enum AlgoType {

    /** 协同过滤 */
    CF("cf"),
    /** 深度模型 */
    DEEP("deep"),
    /** 热度图兜底：新用户没有行为数据时用它填满一屏 */
    HEATMAP("heatmap");

    private final String code;

    AlgoType(String code) {
        this.code = code;
    }

    public static AlgoType of(String code) {
        if (code != null) {
            String normalized = code.trim().toLowerCase(Locale.ROOT);
            for (AlgoType type : values()) {
                if (type.code.equals(normalized)) {
                    return type;
                }
            }
        }
        throw new BizException(ResultCode.VALIDATE_FAILED, "未知的算法类型：" + code + "，可选 cf / deep / heatmap");
    }
}
