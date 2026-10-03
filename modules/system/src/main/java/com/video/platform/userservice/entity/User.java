package com.video.platform.userservice.entity;

import com.video.platform.common.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDate;

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
