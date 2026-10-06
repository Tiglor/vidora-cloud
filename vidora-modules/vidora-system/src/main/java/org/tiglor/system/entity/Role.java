package org.tiglor.system.entity;

import org.tiglor.common.core.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 角色 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_role")
public class Role extends BaseEntity {

    /**
     * 角色编码，如 ROLE_ADMIN / ROLE_USER。有唯一键，登录时原样进 token 的角色列表、
     * 再透传成角色请求头参与鉴权，所以改名等于改一堆 {@code hasRole} 判据。
     * <p>
     * 这张表没有增删改接口，角色与描述都靠 SQL/vidora_cloud.sql 里的种子数据维护。
     */
    private String roleCode;
    /** 角色显示名，如「超级管理员」，只用于界面展示，不参与任何判断 */
    private String roleName;
    /** 角色用途说明，给运营看的备注，后端不读 */
    private String description;
    /** 状态：0-禁用 1-正常。禁用后不再出现在「给用户分配角色」的下拉里，但已经绑了它的用户照样带着这个角色和它名下的权限 */
    private Integer status;
}
