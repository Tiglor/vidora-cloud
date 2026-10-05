package org.tiglor.common.user.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.tiglor.common.core.BaseEntity;

import java.time.LocalDate;

/** 用户，对应 user_service 库的 sys_user 表；由 auth-service 与 system-service 共用。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_user")
public class User extends BaseEntity {

    private String phone;
    private String email;
    private String nickname;
    private String avatarUrl;
    /** 只允许写入，永不随响应体输出，避免 BCrypt 哈希外泄 */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String passwordHash;
    private Integer status;
    private Integer gender;
    private LocalDate birthday;
    private String bio;
    private Integer followCount;
    private Integer followerCount;
    private String region;
    /** 主题标识，取值见前端主题包；后端只存不解释 */
    private String themeKey;
}
