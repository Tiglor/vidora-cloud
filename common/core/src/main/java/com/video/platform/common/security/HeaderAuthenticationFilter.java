package com.video.platform.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 从网关透传的请求头还原 Spring Security 认证上下文。
 * <p>
 * 网关已完成 JWT 认证，本过滤器不再验签，只做「身份还原 + 授权信息装载」：
 * 角色与权限都作为 {@link GrantedAuthority}，从而支持
 * {@code hasRole('ADMIN')}、{@code hasAuthority('video:upload')}、{@code @PreAuthorize}。
 * </p>
 */
public class HeaderAuthenticationFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        try {
            String userIdHeader = request.getHeader(SecurityHeaders.USER_ID);
            if (userIdHeader != null && !userIdHeader.isBlank()) {
                List<String> roles = split(request.getHeader(SecurityHeaders.ROLES));
                List<String> perms = split(request.getHeader(SecurityHeaders.PERMISSIONS));

                List<GrantedAuthority> authorities = new ArrayList<>();
                roles.forEach(r -> authorities.add(new SimpleGrantedAuthority(r)));
                perms.forEach(p -> authorities.add(new SimpleGrantedAuthority(p)));

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(userIdHeader, null, authorities);
                SecurityContextHolder.getContext().setAuthentication(authentication);

                Long userId = parseLongOrNull(userIdHeader);
                UserContext.set(userId, roles, perms);
            }
            chain.doFilter(request, response);
        } finally {
            // 避免线程复用导致上下文串号
            UserContext.clear();
            SecurityContextHolder.clearContext();
        }
    }

    private static List<String> split(String value) {
        if (value == null || value.isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    private static Long parseLongOrNull(String v) {
        try {
            return Long.parseLong(v);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
