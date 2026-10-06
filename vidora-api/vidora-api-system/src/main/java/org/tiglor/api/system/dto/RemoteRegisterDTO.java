package org.tiglor.api.system.dto;

import java.io.Serializable;
import lombok.Data;

/**
 * 注册入参。
 * <p>
 * 收的是 {@code passwordHash} 而不是明文密码：BCrypt 编码在 auth-service 做，
 * 明文密码不该出现在任何一条服务间链路上。「新用户默认给哪个角色」不在这个 DTO 里——
 * 那是 RBAC 数据所有者的决定，由 provider 侧写死（见 system-service 的 provider 实现）。
 */
@Data
public class RemoteRegisterDTO implements Serializable {

    /** Dubbo 的 hessian2 序列化要求可序列化。 */
    private static final long serialVersionUID = 1L;

    /** 登录名，provider 侧靠它先查后插；与已有账号或并发插入撞上时统一返回 {@code null} 表示「没建成」，不抛业务异常 */
    private String phone;
    /** 展示昵称；空白时 provider 用手机号兜底，因为这一列在库里不允许为空 */
    private String nickname;
    /** 已经编码好的密码哈希——调用方负责加密，provider 只原样落库，绝不接收明文 */
    private String passwordHash;
}
