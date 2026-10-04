package org.tiglor.content.enums;

import lombok.Getter;
import org.tiglor.common.core.BizException;
import org.tiglor.common.core.ResultCode;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * 推荐流类型，对应 {@code content_feed_config.feed_type}。
 * <p>
 * 用枚举而不是直接收字符串：feed_type 是唯一键的一半，写进去一个 "hot " 或 "Hot"
 * 就会和 "hot" 并存成两套配置，读的一方永远只能读到其中一套。
 * </p>
 */
@Getter
public enum FeedType {

    /** 个性化推荐流 */
    RECOMMEND("recommend"),
    /** 热门流 */
    HOT("hot"),
    /** 关注流 */
    FOLLOW("follow");

    private final String code;

    FeedType(String code) {
        this.code = code;
    }

    public static FeedType of(String value) {
        if (value != null) {
            String normalized = value.trim().toLowerCase(Locale.ROOT);
            for (FeedType type : values()) {
                if (type.code.equals(normalized)) {
                    return type;
                }
            }
        }
        throw new BizException(ResultCode.VALIDATE_FAILED, "未知的流类型：" + value + "，可选 " + all());
    }

    private static String all() {
        return Arrays.stream(values()).map(FeedType::getCode).collect(Collectors.joining(" / "));
    }
}
