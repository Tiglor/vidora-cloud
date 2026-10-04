package org.tiglor.message.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 会话列表里的一行。
 * <p>
 * {@code peerId} 是「对面那个人」，由服务端按当前登录用户从 user_id_a / user_id_b 里挑出来——
 * 归一化存储的会话行对调用方是不可见的，否则前端还得自己判断「我是 A 还是 B」。
 * </p>
 */
@Data
public class ConversationView {

    private Long conversationId;
    private Long peerId;

    private Long lastMsgId;
    private String lastMsgContent;
    private LocalDateTime lastMsgTime;

    /** 当前登录用户在这一侧的未读数 */
    private Integer unreadCount;
}
