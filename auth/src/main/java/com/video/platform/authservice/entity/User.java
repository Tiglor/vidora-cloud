package com.video.platform.authservice.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.video.platform.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/** 认证所需的用户身份投影，对应系统服务的 sys_user 表。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_user")
public class User extends BaseEntity {

    private String phone;
    private String email;
    private String nickname;
    private String avatarUrl;
    private String passwordHash;
    private Integer status;
    private Integer gender;
    private LocalDate birthday;
    private String bio;
    private Integer followCount;
    private Integer followerCount;
    private String region;
}
