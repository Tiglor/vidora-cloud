package org.tiglor.message.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 收件箱里的一条消息。
 * <p>
 * 刻意不直接返回 {@code MessageRecord}：实体上的 {@code status} 是给存储层用的
 * （查询里已经过滤掉 0 了），暴露出去只会让调用方以为还能改它。
 * </p>
 */
@Data
public class MessageView {

    /** {@code message_record} 的主键，删除单条消息时用它；列表按它倒序，越新越大 */
    private Long id;
    /** 消息类型：1-系统通知 2-互动消息 3-私信，取值含义见 {@link org.tiglor.message.enums.MsgType} */
    private Integer msgType;

    /** 0 表示系统发的 */
    private Long senderId;

    /** 收信人；收件箱和话题线返回的行里它恒等于当前登录用户（请求参数指定不了） */
    private Long receiverId;
    /** 消息正文原文，未做截断（会话卡片上的摘要是另一个接口算的） */
    private String content;
    /** 扩展字段，存进去什么字符串就原样返回什么，服务端不解析其结构 */
    private String extra;

    /** 是否已读，由存储层的 is_read 标记换算而来；标记已读后整批变 true */
    private Boolean read;
    /** 第一次被标记已读的时间，未读时为 null */
    private LocalDateTime readTime;
    /** 消息落库时间 */
    private LocalDateTime createTime;
}
