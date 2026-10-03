package com.video.platform.userservice.entity;

import com.video.platform.common.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 角色 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_role")
public class Role extends BaseEntity {

    private String roleCode;
    private String roleName;
    private String description;
    private Integer status;
}
