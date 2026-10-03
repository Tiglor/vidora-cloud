package org.tiglor.common.core.security;

import java.util.List;

/**
 * 当前登录用户上下文（ThreadLocal），业务代码可直接取 userId / 角色 / 权限。
 * 由 {@link HeaderAuthenticationFilter} 在请求入口填充，请求结束清理。
 */
public final class UserContext {

    private static final ThreadLocal<Long> USER_ID = new ThreadLocal<>();
    private static final ThreadLocal<List<String>> ROLES = new ThreadLocal<>();
    private static final ThreadLocal<List<String>> PERMISSIONS = new ThreadLocal<>();

    private UserContext() {
    }

    public static void set(Long userId, List<String> roles, List<String> permissions) {
        USER_ID.set(userId);
        ROLES.set(roles);
        PERMISSIONS.set(permissions);
    }

    public static Long getUserId() {
        return USER_ID.get();
    }

    public static List<String> getRoles() {
        return ROLES.get();
    }

    public static List<String> getPermissions() {
        return PERMISSIONS.get();
    }

    public static void clear() {
        USER_ID.remove();
        ROLES.remove();
        PERMISSIONS.remove();
    }
}
