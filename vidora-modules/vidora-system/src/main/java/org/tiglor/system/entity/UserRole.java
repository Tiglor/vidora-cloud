package org.tiglor.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 用户-角色关联 */
@Data
@TableName("sys_user_role")
public class UserRole {

    /** 自增主键，一行 = 一个「用户拥有某角色」的绑定 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户ID，一个用户可以多行（多角色的权限取并集） */
    private Long userId;
    /** 角色ID：注册时自动绑上默认的普通用户角色，运营换绑走「给用户分配角色」 */
    private Long roleId;
    /** 这次绑定的时间，由服务端插入时统一取当前时间；重新分配会把旧行整批删掉重写 */
    private LocalDateTime createTime;
}
