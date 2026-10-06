package org.tiglor.system.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** 菜单树节点 */
@Data
public class MenuVO {

    /** 菜单ID，即 sys_menu 主键；管理端「分配权限」提交的就是这些 id */
    private Long id;
    /** 父菜单ID，留空时后端按顶级（0）挂树 */
    private Long parentId;
    /** 菜单名，侧边栏上显示的文字 */
    private String menuName;
    /** 节点类型：1-目录 2-菜单 3-按钮。前端只为 2 生成路由，3 不进侧边栏，1 看有没有可渲染的孩子 */
    private Integer menuType;
    /** 前端路由路径，如 {@code /video/list}；目录也带（它的孩子以此为前缀），只有按钮行没有 */
    private String path;
    /** 组件相对路径，前端拿去解析成路由组件；只有类型为「菜单」时才有值 */
    private String component;
    /** 图标组件名（Element Plus 的 PascalCase 原名），写错只是渲染成空白，不报错 */
    private String icon;
    /** 同级内的显示顺序，小的在前；服务端组树时就是按它排的，前端不用再自己排一遍 */
    private Integer sortOrder;
    /** 这一行授予的权限标识，如 {@code video:upload}；按钮行的存在意义就是它 */
    private String permissionCode;
    /** 是否在侧边栏显示：0-隐藏 1-显示。给当前用户的树已经过滤过，所以那边只会看到 1 */
    private Integer visible;
    /** 0-禁用 1-正常。写入接口收的是裸实体所以这个值一直能被改，但 listMenusForUser 按它过滤，读不出来就没法核对 */
    private Integer status;
    /** 孩子节点，已按 sortOrder 排好；叶子是空数组而不是 null，前端可以直接递归 */
    private List<MenuVO> children = new ArrayList<>();
}
