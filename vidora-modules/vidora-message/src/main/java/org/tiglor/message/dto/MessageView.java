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

    private Long id;
    private Integer msgType;

    /** 0 表示系统发的 */
    private Long senderId;

    private Long receiverId;
    private String content;
    private String extra;

    private Boolean read;
    private LocalDateTime readTime;
    private LocalDateTime createTime;
}
