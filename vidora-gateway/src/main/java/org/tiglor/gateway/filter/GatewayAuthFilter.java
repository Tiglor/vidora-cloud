package org.tiglor.gateway.filter;

import io.jsonwebtoken.Claims;
import org.tiglor.common.core.JwtUtil;
import org.tiglor.common.core.security.SecurityHeaders;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 网关统一鉴权过滤器（WebFlux / Reactive）
 * <p>
 * - 白名单：/api/auth/** 直接放行（登录/注册无需 Token）
 * - 其余请求须携带 Authorization: Bearer <token>，校验失败返回 401
 * - 校验通过后，将 userId / clientId 以请求头透传给下游微服务
 * - 管理接口仅允许管理端客户端的 Token 访问，否则返回 403；
 *   管理端的 clientKey 缺省是 admin，可用 {@code gateway.admin-client-key} 覆盖
 * </p>
 */
@Component
public class GatewayAuthFilter implements GlobalFilter, Ordered {

    private static final String AUTH_PREFIX = "Bearer ";

    /** 管理端客户端标识的默认值，与 SQL 种子里 sys_client.client_key='admin' 那行对齐 */
    private static final String DEFAULT_ADMIN_CLIENT_KEY = "admin";

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

    /**
     * 仅限管理端访问的接口前缀。判定用的 clientKey 是 {@code gateway.admin-client-key}
     * （缺省 {@value #DEFAULT_ADMIN_CLIENT_KEY}），必须和 {@code sys_client.client_key} 里管理端那行一致。
     */
    private static final Set<String> ADMIN_PATH_PREFIXES = Set.of(
            "/api/users/",
            "/api/roles/",
            "/api/menus/",
            "/api/security-audits/",
            "/api/categories/",
            "/api/tags/",
            "/api/feed-configs/",
            "/api/hot-searches/",
            "/api/algo-configs/",
            "/api/search/suggests",
            "/api/search/stats",
            "/api/clients/",
            // 只锁后台子路径：/api/comments/** 整体是用户端共用的，不能整个前缀拉黑
            "/api/comments/admin/",
            // 审计日志：记录里带着入参、返回值和客户端 IP，普通用户的 token 不该读到这些
            "/api/oper-logs/",
            "/api/login-logs/"
    );

    private final JwtUtil jwtUtil;

    /**
     * 管理端客户端标识，必须和 {@code sys_client.client_key} 里管理端那行一致。
     * <p>做成可配置是因为它耦合的是库里的数据：运维在「客户端管理」页把这个 key 改掉之后，
     * 写死在代码里的旧值会让所有管理接口静默 403，只能重新发版才修得回来。</p>
     */
    private final String adminClientKey;

    public GatewayAuthFilter(JwtUtil jwtUtil,
                             @Value("${gateway.admin-client-key:" + DEFAULT_ADMIN_CLIENT_KEY + "}") String adminClientKey) {
        this.jwtUtil = jwtUtil;
        this.adminClientKey = adminClientKey;
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
            Claims claims = jwtUtil.parse(token);
            Long userId = claims.get(JwtUtil.CLAIM_USER_ID, Long.class);
            String roles = JwtUtil.claimString(claims, JwtUtil.CLAIM_ROLES);
            String perms = JwtUtil.claimString(claims, JwtUtil.CLAIM_PERMS);
            String clientId = JwtUtil.claimString(claims, JwtUtil.CLAIM_CLIENT_ID);
            String clientKey = JwtUtil.claimString(claims, JwtUtil.CLAIM_CLIENT_KEY);

            // 4. 管理接口拦截：非 admin 客户端禁止访问
            if (isAdminPath(path) && !adminClientKey.equals(clientKey)) {
                return forbidden(exchange);
            }

            // 5. 透传身份给下游：userId + 角色 + 权限 + 客户端标识
            ServerHttpRequest mutated = exchange.getRequest().mutate()
                    .header(SecurityHeaders.USER_ID, String.valueOf(userId))
                    .header(SecurityHeaders.ROLES, roles)
                    .header(SecurityHeaders.PERMISSIONS, perms)
                    .header(SecurityHeaders.CLIENT_ID, clientId)
                    .header(SecurityHeaders.CLIENT_KEY, clientKey)
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
        if (VIDEO_DETAIL_PATH.matcher(path).matches()) {
            return true;
        }
        if (PUBLIC_GET_PATHS.contains(path)) {
            return true;
        }
        return PUBLIC_GET_PREFIXES.stream().anyMatch(path::startsWith);
    }

    private boolean isAdminPath(String path) {
        return ADMIN_PATH_PREFIXES.stream().anyMatch(path::startsWith);
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().add(HttpHeaders.CONTENT_TYPE, "application/json;charset=UTF-8");
        return exchange.getResponse().setComplete();
    }

    private Mono<Void> forbidden(ServerWebExchange exchange) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.FORBIDDEN);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"code\":403,\"message\":\"该接口仅限管理端访问\"}";
        DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        return -1;
    }
}
