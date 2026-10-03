package com.video.platform.authservice.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.video.platform.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 权限身份投影，对应系统服务的 sys_menu 表。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_menu")
public class Menu extends BaseEntity {

    private Long parentId;
    private String menuName;
    private Integer menuType;
    private String path;
    private String component;
    private String icon;
    private Integer sortOrder;
    private String permissionCode;
    private Integer visible;
    private Integer status;
}
