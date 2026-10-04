package org.tiglor.message.dto;

import lombok.Data;

/**
 * 未读汇总，给客户端的红点用。
 * <p>
 * 数字一律来自 {@code message_record.is_read}（已读状态的唯一真相），
 * 不是把各会话的 unread_count 加起来——后者是派生缓存，两边可能对不上，
 * 红点上出现两个不同的数字比慢一点更糟。
 * </p>
 */
@Data
public class UnreadSummary {

    private Long systemCount;
    private Long interactCount;
    private Long privateCount;

    /** 三类之和 */
    private Long total;

    /** 还有多少段会话有未读私信，给「消息」入口的角标用 */
    private Long conversationCount;
}
