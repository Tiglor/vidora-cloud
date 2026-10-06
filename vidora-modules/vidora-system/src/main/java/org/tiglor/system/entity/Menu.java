package org.tiglor.system.entity;

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

    /** 父菜单ID，0 或留空都表示挂成顶级节点 */
    private Long parentId;
    /** 菜单名，管理端侧边栏上显示的那个名字 */
    private String menuName;
    /** 节点类型：1-目录 2-菜单 3-按钮。用户菜单树只回 1 和 2，3 不生成路由，只作为权限位存在 */
    private Integer menuType;
    /** 前端路由路径，如 {@code /video/list}；按钮节点没有路由，留空 */
    private String path;
    /** 前端组件相对路径，如 {@code pages/video/list}，由管理端动态路由表拿去解析；目录与按钮留空 */
    private String component;
    /** 菜单图标，存的是 Element Plus 图标组件名（须是 PascalCase 原名），写错不报错、只是渲染成空白 */
    private String icon;
    /** 同级内的显示顺序，小的排前面；留空时前端按最后处理 */
    private Integer sortOrder;
    /** 接口级鉴权用的权限标识，会经「角色 → 菜单」汇总进登录时签发的权限列表；同一个标识可以挂在多行上 */
    private String permissionCode;
    /** 是否在侧边栏显示：0-隐藏 1-显示。置 0 只是不画出来，已授权该行的用户仍拿得到它的权限标识 */
    private Integer visible;
    /** 状态：0-禁用 1-正常。禁用后不出现在用户菜单树里，但它的权限标识仍会被授予给绑定了该行的角色 */
    private Integer status;
}
