package org.tiglor.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 角色-菜单关联（授权） */
@Data
@TableName("sys_role_menu")
public class RoleMenu {

    /** 自增主键，一行 = 一条「这个角色有这个菜单」的授权关系 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 角色ID，授权按它整批删旧插新 */
    private Long roleId;
    /** 被授权的菜单行ID：既包括侧边栏看得见的目录/菜单，也包括 visible=0 的按钮权限行——后者才是接口鉴权的来源 */
    private Long menuId;
    /** 这次授权的时间，由服务端插入时统一取当前时间，请求体传了也不生效；这张表没有单独改某一行的接口 */
    private LocalDateTime createTime;
}
