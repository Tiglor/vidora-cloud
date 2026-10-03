package org.tiglor.gateway.filter;

import org.tiglor.common.core.JwtUtil;
import org.tiglor.common.core.security.SecurityHeaders;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 网关统一鉴权过滤器（WebFlux / Reactive）
 * <p>
 * - 白名单：/api/auth/** 直接放行（登录/注册无需 Token）
 * - 其余请求须携带 Authorization: Bearer <token>，校验失败返回 401
 * - 校验通过后，将 userId 以 X-User-Id 头透传给下游微服务
 * </p>
 */
@Component
public class GatewayAuthFilter implements GlobalFilter, Ordered {

    private static final String AUTH_PREFIX = "Bearer ";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        // 1. 白名单放行
        if (path.startsWith("/api/auth/")) {
            return chain.filter(exchange);
        }

        // 2. 校验 Token
        String auth = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (auth == null || !auth.startsWith(AUTH_PREFIX)) {
            return unauthorized(exchange);
        }
        String token = auth.substring(AUTH_PREFIX.length());
        try {
            if (JwtUtil.isExpired(token)) {
                return unauthorized(exchange);
            }
            Long userId = JwtUtil.getUserId(token);
            String roles = JwtUtil.getRoles(token);
            String perms = JwtUtil.getPerms(token);

            // 3. 透传身份给下游：userId + 角色 + 权限（下游 Spring Security 据此做授权）
            ServerHttpRequest mutated = exchange.getRequest().mutate()
                    .header(SecurityHeaders.USER_ID, String.valueOf(userId))
                    .header(SecurityHeaders.ROLES, roles)
                    .header(SecurityHeaders.PERMISSIONS, perms)
                    .build();
            return chain.filter(exchange.mutate().request(mutated).build());
        } catch (Exception e) {
            return unauthorized(exchange);
        }
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().add(HttpHeaders.CONTENT_TYPE, "application/json;charset=UTF-8");
        // 前端统一对 401 跳登录，这里无需返回 body
        return exchange.getResponse().setComplete();
    }

    @Override
    public int getOrder() {
        // 早于路由过滤器执行
        return -1;
    }
}
