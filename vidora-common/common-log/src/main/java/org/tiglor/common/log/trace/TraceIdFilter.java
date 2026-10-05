package org.tiglor.common.log.trace;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import org.tiglor.common.core.support.TraceIds;

import java.io.IOException;

/**
 * 给每个请求绑定 traceId。
 * <p>
 * 上游（网关）带了就沿用，没带就自己生成一个 —— 服务被定时任务、MQ 消费者或
 * 其它服务直接调用时走的都是内网路径，没有网关那一跳，缺这一步这些日志就没有关联键。
 * 同时回写到响应头，前端报障时可以直接把 traceId 贴过来。
 * <p>
 * 排在最前：认证、限流、业务过滤器打的日志也要带上同一个 traceId，
 * 晚一步绑定就只剩它们那几行没有链路号。
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String traceId = request.getHeader(TraceIds.HEADER);
        if (!StringUtils.hasText(traceId)) {
            traceId = TraceIds.newTraceId();
        }
        MDC.put(TraceIds.MDC_KEY, traceId);
        response.setHeader(TraceIds.HEADER, traceId);
        try {
            chain.doFilter(request, response);
        } finally {
            // 线程会被线程池复用，不清理就会串到下一个请求的日志上
            MDC.remove(TraceIds.MDC_KEY);
        }
    }
}
