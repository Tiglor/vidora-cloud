package org.tiglor.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UserRegisterDTO {

    /** 手机号，即登录名；已被占用时 provider 返回空、本服务统一报「手机号已注册」。不做格式校验，只要非空白 */
    @NotBlank(message = "手机号不能为空")
    private String phone;

    /** 明文密码，只在 auth-service 内做 BCrypt，编码后的哈希才跨进程传给 system-service；新账号因此可直接登录，而管理端手工插的用户建不出能登录的账号 */
    @NotBlank(message = "密码不能为空")
    private String password;

    /** 展示昵称，必填；本接口不回显它，注册成功只返回新用户 id */
    @NotBlank(message = "昵称不能为空")
    private String nickname;
}
