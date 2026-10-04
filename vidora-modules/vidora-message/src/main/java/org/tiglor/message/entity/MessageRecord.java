package org.tiglor.message.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 一条消息。系统通知、互动消息、私信共用一张表，靠 {@code msgType} 区分。
 * <p>
 * 不继承 {@code BaseEntity}：表上没有 {@code is_deleted} 也没有 {@code update_time}。
 * 删除是「对读者隐藏」，靠 {@code status} 表达；已读另有 {@code isRead} / {@code readTime} 两列，
 * 再叠一层逻辑删除只会让「已读的删除消息」这种组合无从表达。
 * </p>
 * <p>
 * {@code isRead} 是已读状态的**唯一真相**，会话表上的 unread_count_a/b 只是它的派生缓存，
 * 见 {@link org.tiglor.message.service.ConversationService}。
 * </p>
 */
@Data
@TableName("message_record")
public class MessageRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 消息类型，取值见 {@link org.tiglor.message.enums.MsgType} */
    private Integer msgType;

    /** 发送者，0 表示系统 */
    private Long senderId;

    private Long receiverId;

    private String content;

    /** 扩展字段，DDL 上是 JSON 列，这里按字符串原样存取（跳转链接、被点赞的对象 id 之类） */
    private String extra;

    /** 是否已读：0-未读 1-已读 */
    private Integer isRead;

    private LocalDateTime readTime;

    /** 状态：0-已删除 1-正常 */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
