package org.tiglor.api.system.dto;

import java.io.Serializable;
import lombok.Data;

/**
 * 客户端（{@code sys_client}）的传输视图。
 * <p>
 * 字段是 {@code org.tiglor.system.entity.Client} 的显式子集，由 system-service 侧负责保持对齐。
 * 登录时 auth-service 要靠它判「这个端能不能用密码登录」以及 token 该活多久。
 */
@Data
public class RemoteClientDTO implements Serializable {

    /** Dubbo 的 hessian2 序列化要求可序列化。 */
    private static final long serialVersionUID = 1L;

    /** 客户端标识，前端写死、库里的唯一键；命中不了这一行 provider 直接返回 null，调用方据此判定「未注册」 */
    private String clientId;
    /** 端的短名（如 web / mobile / admin），同样唯一；会被签进 JWT 并由网关透传给下游，登录审计记的就是它 */
    private String clientKey;
    /** 设备类型（如 pc / app），目前只是登记表里的说明信息，登录与鉴权链路都不读它 */
    private String deviceType;
    /** 逗号分隔的授权类型集合，如 {@code password,sms}；调用方按 contains 判断。 */
    private String grantType;
    /** token 有效期（秒），直接进 JWT 的过期时间。 */
    private Integer timeout;
    /** 0-停用 1-启用。 */
    private Integer status;
}
