package org.tiglor.search.enums;

import org.tiglor.common.core.BizException;
import org.tiglor.common.core.ResultCode;

/**
 * 建议词的来源，对应 {@code search_suggest.source}。
 * <p>
 * 人工录入和自动挖掘要能区分开：挖掘出来的词没人审过，一旦联想框里出现不该出现的词，
 * 运营需要能只按来源筛出来批量禁用，而不是把整张表翻一遍。
 * </p>
 */
public enum SuggestSource {

    /** 运营手工维护 */
    MANUAL(1),

    /** 从 {@code search_keyword_stat} 里按热度挖掘出来的，见 {@code SearchSuggestService.mine} */
    MINED(2);

    private final int code;

    SuggestSource(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    /**
     * @param code null 表示「调用方没指定」，由调用方决定默认值，这里不猜
     */
    public static SuggestSource of(Integer code) {
        if (code == null) {
            return null;
        }
        for (SuggestSource source : values()) {
            if (source.code == code) {
                return source;
            }
        }
        throw new BizException(ResultCode.VALIDATE_FAILED,
                "未知的建议词来源：" + code + "，可选 1-人工 2-自动挖掘");
    }
}
