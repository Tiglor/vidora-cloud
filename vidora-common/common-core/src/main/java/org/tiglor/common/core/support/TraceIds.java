package org.tiglor.common.core.support;

import java.util.UUID;

/**
 * traceId 的键名约定。
 * <p>
 * 网关为每个入口请求生成一个 traceId 并向下透传，各服务把它塞进 MDC，
 * 于是日志格式里的 {@code traceId=xxx}、审计表的 trace_id 列与响应头 {@code X-Trace-Id}
 * 用的是同一个值。
 * <p>
 * 放在 common-core 而不是 common-log：网关要用头名，但它 WebFlux 起不来 servlet 侧的
 * common-log（依赖被显式排掉了），头名若在两边各写一遍，改一处漏一处就断链。
 */
public final class TraceIds {

    /** HTTP 头名，网关与服务之间透传 */
    public static final String HEADER = "X-Trace-Id";

    /** MDC 键名，必须与 logback-base.xml 里的 %X{traceId} 保持一致 */
    public static final String MDC_KEY = "traceId";

    private TraceIds() {
    }

    /**
     * 生成一个新的 traceId：UUID 去掉横线取前 16 位。
     * <p>
     * 截短是因为它要出现在每一行日志里，完整 UUID 的长度会让日志明显变宽，
     * 而这里只需要「同一次调用能对上」，不需要全球唯一。
     */
    public static String newTraceId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }
}
