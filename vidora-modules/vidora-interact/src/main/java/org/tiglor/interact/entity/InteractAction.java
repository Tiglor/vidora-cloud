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

    /** 主键，数据库自增。点赞 / 收藏按 {@code uk_user_target_action}（userId + targetType + targetId + actionType）一行存到底：取消只改 status，再点回来复用同一行，所以它不是「第几次动作」的序号 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 做动作的用户，取当前登录用户；未登录直接拒，所以这一列不会是 null */
    private Long userId;

    /** 对象类型：video / comment */
    private String targetType;

    /**
     * 被互动对象的 ID，含义由 {@link #targetType} 决定。
     * <p>服务端不校验它是否真的存在：video_info 归 video-service 写，跨服务校验等于给最热的写路径
     * 加一次远程调用。传个不存在的 ID 只会留下一行没人读的死数据。</p>
     */
    private Long targetId;

    /** 动作类型，取值见 {@link org.tiglor.interact.enums.ActionType} */
    private Integer actionType;

    /** 状态：0-已取消 1-有效 */
    private Integer status;

    /** 首次做出该动作的时间，只在插入那一次填充，之后再点取消/重新点赞都不会变 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 最后一次状态翻转（含取消）的时间，收藏列表按它倒序，所以是「最近收藏」而不是「首次收藏」 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
