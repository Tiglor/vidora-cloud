package org.tiglor.common.user.entity;

import org.tiglor.common.core.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 菜单 / 权限
 * menuType：1-目录 2-菜单 3-按钮
 * permissionCode：权限标识，用于接口级鉴权（如 video:upload）
 */
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
