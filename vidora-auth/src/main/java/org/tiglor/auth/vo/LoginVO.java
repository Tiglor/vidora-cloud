package org.tiglor.auth.vo;

import lombok.Data;

import java.util.List;

/** 登录结果：Token、用户身份、角色与权限标识。 */
@Data
public class LoginVO {

    private String token;
    private Long userId;
    private String nickname;
    private String avatarUrl;
    private List<String> roles;
    private List<String> permissions;
    private String clientId;
    private String clientKey;
    private Long expiresIn;
    /** 用户自选主题标识，前端据此决定首屏配色 */
    private String themeKey;
}
