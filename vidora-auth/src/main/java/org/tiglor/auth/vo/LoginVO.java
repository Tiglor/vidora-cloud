package org.tiglor.auth.vo;

import lombok.Data;

import java.util.List;

/** 登录结果：Token、用户身份、角色与权限标识。 */
@Data
public class LoginVO {

    /**
     * 签名后的 JWT，后续请求放在 Authorization 头里带上；网关验签通过后把它拆成身份头发给下游。
     * <p>
     * payload 含 userId / roles / perms / clientId / clientKey，所以角色与权限在这里是「随 token 一起发出去」的，
     * 改权限必须等这张 token 过期才生效——下面那几个字段是给前端本地渲染用的同一份数据，不是权威来源。
     * </p>
     */
    private String token;
    /** 用户 id，与 token 里的 claim 同源；自助接口的身份一律取网关透传的登录态，不认前端回传的这个值 */
    private Long userId;
    /** 昵称，直接取自 sys_user，未做兜底加工 */
    private String nickname;
    /** 头像地址，用户没设置过时为 null */
    private String avatarUrl;
    /** 角色标识列表，来自 RBAC；没有任何角色时是空列表而不是 null，可直接遍历 */
    private List<String> roles;
    /** 权限标识列表，用于前端按钮级显隐；真正的接口鉴权仍由服务端逐条判，前端藏掉按钮不算权限控制。无权限时是空列表 */
    private List<String> permissions;
    /** 登录时提交的那个客户端标识，原样回显；同时写进了 token */
    private String clientId;
    /** 端的短名（web / mobile / admin），比 clientId 更适合做「当前是哪一端」的判断；审计日志记的就是它 */
    private String clientKey;
    /** token 有效期，单位「秒」而不是毫秒，取自该客户端在 sys_client 上配的固定过期；各端不同（管理端明显短于移动端）。到期需重新登录，本服务不提供刷新接口 */
    private Long expiresIn;
    /** 用户自选主题标识，前端据此决定首屏配色 */
    private String themeKey;
}
