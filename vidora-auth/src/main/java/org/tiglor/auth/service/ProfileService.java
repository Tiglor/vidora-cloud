package org.tiglor.auth.service;

/**
 * 当前登录用户的自助偏好设置。
 * <p>
 * 与 system-service 的 UserController 严格分开：那边是管理员改「任何用户」，
 * 这边是用户改「自己」，两者鉴权模型完全不同。
 * </p>
 */
public interface ProfileService {

    /**
     * 修改主题。
     *
     * @param userId   取自 UserContext，绝不接受请求体传入，否则就能改别人的
     * @param themeKey 主题标识
     */
    void updateTheme(Long userId, String themeKey);
}
