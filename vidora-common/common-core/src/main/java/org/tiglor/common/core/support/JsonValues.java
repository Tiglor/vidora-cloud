package org.tiglor.common.core.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.tiglor.common.core.BizException;
import org.tiglor.common.core.ResultCode;

/**
 * JSON 字符串的写入前校验。
 * <p>
 * 好几个模块都有「列约定存 JSON」的字段（{@code content_security_audit.machine_result} 干脆就是 JSON 列，
 * {@code content_feed_config.config_value} 和 {@code recommend_algo_config.config_value} 是 TEXT 但语义相同）。
 * JSON 列尤其要先验：MySQL 收到非法 JSON 会抛 3140，那个错误穿过
 * {@link org.tiglor.common.core.GlobalExceptionHandler} 就只剩一句「服务异常」，
 * 运营根本不知道是自己填错了。
 * </p>
 * <p>
 * 放在 common-core 而不是各模块自己抄一份：校验语义必须完全一致，
 * 否则同一个错误值在 content 报 400、在 recommend 报 500。
 * </p>
 */
public final class JsonValues {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private JsonValues() {
    }

    /**
     * 校验一段字符串是合法 JSON；空白原样返回（这类列都允许 NULL，
     * 空白表示「把这个值清掉」而不是「存一个空 JSON」）。
     *
     * @param field 出错时报给调用方的字段名
     */
    public static String requireValid(String value, String field) {
        if (value == null || value.isBlank()) {
            return value;
        }
        try {
            MAPPER.readTree(value);
        } catch (JsonProcessingException e) {
            throw new BizException(ResultCode.VALIDATE_FAILED,
                    field + " 不是合法 JSON：" + e.getOriginalMessage());
        }
        return value;
    }
}
