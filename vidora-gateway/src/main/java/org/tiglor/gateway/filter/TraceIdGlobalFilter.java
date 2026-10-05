package org.tiglor.gateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import org.tiglor.common.core.support.TraceIds;
import reactor.core.publisher.Mono;

/**
 * 网关侧的 traceId 起点：给每个入口请求定一个链路号，透传给下游并回写响应头。
 * <p>
 * 下游各服务的 TraceIdFilter 沿用同一个值，所以一次请求在 gateway-service、
 * 业务服务的 app.log/error.log 里都能用同一个 traceId 串起来，
 * 审计表 sys_oper_log.trace_id 记的也是它。
 * <p>
 * 刻意不写 MDC：网关是 WebFlux，一个 Netty event-loop 线程会交替推进多个请求，
 * 线程绑定的 MDC 在这种模型下会串号，打出来的链路号比没有更难查。
 * 链路号在本服务只作为头透传，落日志由下游的 servlet 侧完成。
 */
@Component
public class TraceIdGlobalFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String traceId = exchange.getRequest().getHeaders().getFirst(TraceIds.HEADER);
        if (!StringUtils.hasText(traceId)) {
            traceId = TraceIds.newTraceId();
        }

        // 响应头让前端拿到：用户报障时直接贴这串，比描述「我点了哪个按钮」准
        exchange.getResponse().getHeaders().set(TraceIds.HEADER, traceId);

        ServerHttpRequest mutated = exchange.getRequest().mutate()
                .header(TraceIds.HEADER, traceId)
                .build();
        return chain.filter(exchange.mutate().request(mutated).build());
    }

    @Override
    public int getOrder() {
        // 必须早于 GatewayAuthFilter(-1)：认证失败直接返回 401 时，响应头里也要带上链路号
        return -100;
    }
}
