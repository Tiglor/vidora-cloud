package org.tiglor.common.log.sink;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.util.regex.Pattern;

/**
 * 审计流水用的 ObjectMapper。
 * <p>
 * 单独一个而不是复用容器里的：容器那份在 Boot 4 下是 Jackson 3（tools.jackson），
 * 本项目其余序列化走的是 com.fasterxml 的 Jackson 2，两边混用只会让人以为配置没生效。
 * 注册 JavaTimeModule 是必须的，否则 LocalDateTime 直接抛异常。
 * <p>
 * {@link #write} 顺带做敏感字段脱敏，所以凡是「要落审计的 JSON」都走这里，
 * 不要在自己类里 new ObjectMapper —— 那样等于绕过了脱敏。
 */
public final class AuditJson {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            // 没有可读属性的对象序列化成 {}，比抛异常退化成「serialize failed」有用
            .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    /**
     * 键名里含 password / passwd / secret / token 的字段，值整段替换成 ***。
     * <p>
     * 按「名字里带这些词」匹配而不是列一张字段表：新增一个 {@code pushToken}、{@code clientSecret}
     * 时没人会想起来改审计模块的清单，而漏一次的后果是明文口令永久躺在审计表里。
     * 值侧同时兼容字符串和标量（true/false/null/数字），否则 {@code "password":123456} 这种
     * 传错的请求体就漏出去了。
     */
    private static final Pattern SENSITIVE_VALUE = Pattern.compile(
            "(\"[^\"]*(?:password|passwd|secret|token)[^\"]*\"\\s*:\\s*)"
                    + "(?:\"(?:[^\"\\\\]|\\\\.)*\"|true|false|null|-?\\d+(?:\\.\\d+)?)",
            Pattern.CASE_INSENSITIVE);

    private AuditJson() {
    }

    public static String write(Object value) {
        try {
            return desensitize(MAPPER.writeValueAsString(value));
        } catch (Exception e) {
            // 审计行序列化失败不能把业务带崩，退化成一句可 grep 的占位
            return "{\"error\":\"" + value.getClass().getSimpleName() + " serialize failed: "
                    + String.valueOf(e.getMessage()).replace('"', '\'') + "\"}";
        }
    }

    private static String desensitize(String json) {
        return SENSITIVE_VALUE.matcher(json).replaceAll("$1\"***\"");
    }
}
