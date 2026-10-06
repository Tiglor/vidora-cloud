package org.tiglor.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UserLoginDTO {

    /** 手机号，即登录名，按唯一键精确匹配（不做格式校验）；账号不存在与密码错误返回同一句提示，防手机号枚举。它同时也是登录日志里记的 username */
    @NotBlank(message = "手机号不能为空")
    private String phone;

    /** 明文密码，只在 auth-service 内与库里的 BCrypt 哈希比对，不会以任何形式转发给 system-service */
    @NotBlank(message = "密码不能为空")
    private String password;

    /** 客户端标识，需命中 sys_client 且状态为启用、授权方式包含 password；token 的有效期也由这一行的配置决定，所以不同端的登录态寿命不一样 */
    @NotBlank(message = "客户端ID不能为空")
    private String clientId;
}
