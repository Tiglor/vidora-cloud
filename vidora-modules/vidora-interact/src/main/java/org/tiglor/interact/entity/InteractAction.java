package org.tiglor.interact.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 点赞 / 收藏 / 分享记录，一行代表「某用户对某对象做过某动作」。
 * <p>
 * 刻意不继承 {@code BaseEntity}：表上没有 is_deleted 列，而且「取消」是靠 status 翻转表达的——
 * 真加逻辑删除的话，取消后再想点赞就会被 uk_user_target_action 挡住（旧行还占着唯一键）。
 * </p>
 */
@Data
@TableName("interact_action")
public class InteractAction implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** 对象类型：video / comment */
    private String targetType;

    private Long targetId;

    /** 动作类型，取值见 {@link org.tiglor.interact.enums.ActionType} */
    private Integer actionType;

    /** 状态：0-已取消 1-有效 */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
