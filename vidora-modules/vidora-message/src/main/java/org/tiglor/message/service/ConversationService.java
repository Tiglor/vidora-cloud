package org.tiglor.message.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.tiglor.message.dto.ConversationView;

import java.time.LocalDateTime;

/**
 * 私信会话。
 * <p>
 * 会话行是**派生数据**：它由私信消息聚合而来，存在的意义只是让「会话列表」不必每次都对
 * {@code message_record} 做一遍 GROUP BY。因此未读数的唯一真相是 {@code message_record.is_read}，
 * 这里的 unread_count_a/b 是它的缓存，标记已读时按实测值**重算**而不是直接清 0。
 * </p>
 * <p>
 * 存储上 (user_id_a, user_id_b) 一律归一化成 (min, max)，本接口的入参和出参都用「我 / 对面」表达，
 * 归一化不外泄——调用方不需要知道自己在这一行里是 A 还是 B。
 * </p>
 */
public interface ConversationService {

    /**
     * 一条私信发出后推进会话：没有这段会话就建一行，有就更新「最后一条消息」并给收信方 +1 未读。
     *
     * @param senderId   发信人
     * @param receiverId 收信人，不能与发信人相同
     * @param lastMsgId  刚插入的 {@code message_record.id}
     * @param lastMsgTime 该消息的创建时间
     */
    void touchOnSend(Long senderId, Long receiverId, Long lastMsgId, LocalDateTime lastMsgTime);

    /** 我的会话列表，按最后一条消息的时间倒序，每行带最后一条消息的内容摘要 */
    Page<ConversationView> list(Long userId, long current, long size);

    /**
     * 把与某人的私信全部标记已读，并把这一侧的未读数同步成实测值。
     *
     * @return 被标记的消息条数
     */
    int markRead(Long userId, Long peerId);

    /** 「私信全部已读」之后把我在所有会话里的未读数清零 */
    void clearAllUnread(Long userId);

    /** 还有未读私信的会话段数 */
    long countWithUnread(Long userId);
}
