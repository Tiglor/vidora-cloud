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

    /** 消息主键，数据库自增；收件箱与话题线都以它倒序排列，等价于「越新越大」的稳定游标 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 消息类型，取值见 {@link org.tiglor.message.enums.MsgType} */
    private Integer msgType;

    /** 发送者，0 表示系统 */
    private Long senderId;

    /** 这条消息投给谁；私信同样只存单个收信人，一段对话靠两行互为收发拼出来 */
    private Long receiverId;

    /** 正文，入库前已去掉首尾空白；会话列表上的摘要由查询侧再截断，不改这里的原文 */
    private String content;

    /** 扩展字段，DDL 上是 JSON 列，这里按字符串原样存取（跳转链接、被点赞的对象 id 之类） */
    private String extra;

    /** 是否已读：0-未读 1-已读 */
    private Integer isRead;

    /** 第一次被标记已读的时间；同一批「全部已读」重复执行不会再刷新它 */
    private LocalDateTime readTime;

    /** 状态：0-已删除 1-正常 */
    private Integer status;

    /** 消息落库时间，插入时自动填充；私信发出后拿它去推进会话的 last_msg_time */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
