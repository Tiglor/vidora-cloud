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

    /** 系统通知（msgType=1）里我还没读的条数 */
    private Long systemCount;
    /** 互动消息（msgType=2）里我还没读的条数 */
    private Long interactCount;
    /** 私信（msgType=3）里我还没读的条数，跨所有会话合计，不分段 */
    private Long privateCount;

    /** 三类之和 */
    private Long total;

    /** 还有多少段会话有未读私信，给「消息」入口的角标用 */
    private Long conversationCount;
}
