package org.tiglor.gateway.filter;

import io.jsonwebtoken.Claims;
import org.tiglor.common.core.JwtUtil;
import org.tiglor.common.core.security.SecurityHeaders;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Set;
import java.util.regex.Pattern;

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

    /** 视频详情路径：/api/videos/{数字id} 及其子路径（如 /owner、/play-url、/download） */
    private static final Pattern VIDEO_DETAIL_PATH = Pattern.compile("^/api/videos/\\d+(/.*)?$");

    /** 匿名可访问的 GET 精确路径（浏览类接口，无需登录） */
    private static final Set<String> PUBLIC_GET_PATHS = Set.of(
            "/api/videos/page",
            "/api/categories/list",
            "/api/categories/tree",
            "/api/tags/hot",
            "/api/tags/suggest",
            "/api/search/suggests",
            "/api/actions/counts",
            "/api/recommends/feed",
            "/api/hot-searches"
    );

    /** 匿名可访问的 GET 路径前缀（含路径变量的浏览接口） */
    private static final Set<String> PUBLIC_GET_PREFIXES = Set.of(
            "/api/comments/video/",
            "/api/comments/replies/",
            "/api/play-counts/",
            "/api/danmaku/video/"
    );

    private final JwtUtil jwtUtil;

    public GatewayAuthFilter(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        // 1. 白名单放行：登录/注册
        if (path.startsWith("/api/auth/")) {
            return chain.filter(exchange);
        }

        // 2. 浏览类 GET 接口放行（视频列表/详情、分类、标签、搜索），
        //    让访客无需登录即可浏览内容；写操作仍需鉴权
        if (isPublicBrowse(exchange.getRequest().getMethod(), path)) {
            return chain.filter(exchange);
        }

        // 3. 校验 Token
        String auth = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (auth == null || !auth.startsWith(AUTH_PREFIX)) {
            return unauthorized(exchange);
        }
        String token = auth.substring(AUTH_PREFIX.length());
        try {
            // 过期、签名非法、格式错误均由 parse 抛异常，统一走 401
            Claims claims = jwtUtil.parse(token);
            Long userId = claims.get(JwtUtil.CLAIM_USER_ID, Long.class);
            String roles = JwtUtil.claimString(claims, JwtUtil.CLAIM_ROLES);
            String perms = JwtUtil.claimString(claims, JwtUtil.CLAIM_PERMS);

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

    private boolean isPublicBrowse(HttpMethod method, String path) {
        if (method != HttpMethod.GET) {
            return false;
        }
        // 视频详情：/api/videos/{id} 及其子路径
        if (VIDEO_DETAIL_PATH.matcher(path).matches()) {
            return true;
        }
        // 精确匹配的浏览接口
        if (PUBLIC_GET_PATHS.contains(path)) {
            return true;
        }
        // 前缀匹配的浏览接口（含路径变量）
        return PUBLIC_GET_PREFIXES.stream().anyMatch(path::startsWith);
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
