package org.tiglor.api.system.dto;

import java.io.Serializable;
import java.util.List;
import lombok.Data;

/**
 * 登录专用的用户聚合视图：一次调用把「验密码 + 判状态 + 发 token 要用的角色与权限」全带回来。
 * <p>
 * 刻意和 {@link RemoteUserDTO} 分开，而不是往那个类上加字段：{@code RemoteUserDTO} 走 HTTP
 * 出口（{@code VideoController.owner}），任何加在它身上的字段都会跟着 JSON 发给前端；
 * 而本类里的 {@code passwordHash} 只该在 auth-service 与 system-service 之间的 RPC 里出现。
 * <p>
 * 把角色和权限一起打包是 provider 侧批量的考虑：拆成三次调用（用户 / 角色码 / 权限码）
 * 会让每次登录多两个网络往返，而这三份数据本来就在同一个库、同一次事务视图里。
 */
@Data
public class RemoteLoginUserDTO implements Serializable {

    /** Dubbo 的 hessian2 序列化要求可序列化。 */
    private static final long serialVersionUID = 1L;

    /** 用户 id，调用方拿它签进 JWT 并回填登录响应 */
    private Long id;
    /** 昵称，provider 原样透传库里的列；账号若由管理端直接建出，这里可能就是手机号 */
    private String nickname;
    /** 头像地址，用户未设置过时为 null——provider 原样透传库里的列，不做占位图兜底 */
    private String avatarUrl;
    /** 0-禁用 1-正常。禁用判断留给调用方，provider 不替它决定「禁用算不算登录失败」。 */
    private Integer status;
    /**
     * BCrypt 哈希，仅供调用方做 {@code PasswordEncoder.matches}。
     * <p>
     * 传哈希而不是传明文密码去让 provider 校验：明文密码一旦上网线就进了对端的日志与内存转储面，
     * 而哈希本来就是不可逆的。也不由 provider 直接判密码对错——「账号不存在与密码错误必须返回同一提示」
     * 这条防枚举策略属于认证服务，落在它那边才改得动。
     */
    private String passwordHash;
    /** 用户自选主题，随登录一起回来，省掉首屏再拉一次偏好。 */
    private String themeKey;
    /** 角色标识（{@code sys_role.role_code}），无角色时是空列表而不是 null。 */
    private List<String> roles;
    /** 权限标识（{@code sys_menu.permission_code}），无权限时是空列表而不是 null。 */
    private List<String> permissions;
}
