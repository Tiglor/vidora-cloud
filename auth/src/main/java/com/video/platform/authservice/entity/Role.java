package com.video.platform.authservice.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.video.platform.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 角色身份投影，对应系统服务的 sys_role 表。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_role")
public class Role extends BaseEntity {

    private String roleCode;
    private String roleName;
    private String description;
    private Integer status;
}
