package com.video.platform.common.security;

/**
 * 网关 → 下游微服务 传递身份信息的请求头常量。
 * <p>
 * 认证在网关完成（校验 JWT），下游服务只做授权（Authorization），
 * 因此网关必须把这些头透传下来；下游服务据此还原 Spring Security 上下文。
 * </p>
 */
public final class SecurityHeaders {

    private SecurityHeaders() {
    }

    /** 用户ID */
    public static final String USER_ID = "X-User-Id";
    /** 角色，逗号分隔，如 ROLE_ADMIN,ROLE_USER */
    public static final String ROLES = "X-User-Roles";
    /** 权限标识，逗号分隔，如 video:upload,comment:list */
    public static final String PERMISSIONS = "X-User-Permissions";
}
