package org.tiglor.message.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.tiglor.message.entity.MessageConversation;

import java.time.LocalDateTime;

/**
 * 会话表的读写。
 * <p>
 * 三个写方法都要求调用方先把 (userIdA, userIdB) 归一化成 (min, max)——
 * {@code uk_conversation} 是有序唯一键，不归一化就会把同一段对话劈成两行。
 * 归一化统一放在 {@code ConversationServiceImpl} 里做，这里只负责 SQL。
 * </p>
 */
@Mapper
public interface MessageConversationMapper extends BaseMapper<MessageConversation> {

    /**
     * 发出一条私信后推进会话：不存在就建一行，存在就更新「最后一条消息」并给收信方 +1 未读。
     * <p>
     * 一条语句完成「建或改」，不用先查再决定——两个用户同时给对方发消息时，
     * 先查后插的两条请求会双双撞到 {@code uk_conversation} 上。
     * </p>
     * <p>
     * {@code unread_count_*} 用 {@code 列 + VALUES(列)} 累加而不是赋绝对值：
     * 调用方只知道「这一条要让对方多一个未读」，不知道对方当前累积了多少，
     * 赋绝对值就得先读一次，并发下互相覆盖。收信方是 A 还是 B 由调用方算好，
     * 两个增量里恒有一个是 0。
     * </p>
     * <p>
     * 「最后一条消息」用 GREATEST 取较新者：两个用户同时发消息时，
     * id 较小的那条不一定后到，直接赋值会让会话列表的预览偶尔回退到上一条。
     * COALESCE 是给 last_msg_time 为 NULL 的历史行兜底——GREATEST 碰到 NULL 会返回 NULL。
     * </p>
     */
    @Insert("""
            INSERT INTO message_conversation
                (user_id_a, user_id_b, last_msg_id, last_msg_time, unread_count_a, unread_count_b)
            VALUES
                (#{userIdA}, #{userIdB}, #{lastMsgId}, #{lastMsgTime}, #{unreadA}, #{unreadB})
            ON DUPLICATE KEY UPDATE
                last_msg_id     = GREATEST(COALESCE(last_msg_id, 0), VALUES(last_msg_id)),
                last_msg_time   = GREATEST(COALESCE(last_msg_time, VALUES(last_msg_time)), VALUES(last_msg_time)),
                unread_count_a  = unread_count_a + VALUES(unread_count_a),
                unread_count_b  = unread_count_b + VALUES(unread_count_b)
            """)
    int touchOnSend(@Param("userIdA") Long userIdA,
                    @Param("userIdB") Long userIdB,
                    @Param("lastMsgId") Long lastMsgId,
                    @Param("lastMsgTime") LocalDateTime lastMsgTime,
                    @Param("unreadA") int unreadA,
                    @Param("unreadB") int unreadB);

    /**
     * 把 A（id 较小者）的未读数同步成重算出来的值。
     * <p>
     * 是「同步成实测值」而不是「清 0」：标记已读和这里之间可能正好插进来一条新私信，
     * 直接清 0 会让那条消息永远顶着未读却不在红点上。重算的依据是
     * {@code message_record.is_read}（唯一真相），两边不会长期打架。
     * </p>
     */
    @Update("""
            UPDATE message_conversation
               SET unread_count_a = #{unread}
             WHERE user_id_a = #{userIdA} AND user_id_b = #{userIdB}
            """)
    int syncUnreadA(@Param("userIdA") Long userIdA, @Param("userIdB") Long userIdB, @Param("unread") long unread);

    /** B（id 较大者）那一侧的未读数同步，理由同 {@link #syncUnreadA} */
    @Update("""
            UPDATE message_conversation
               SET unread_count_b = #{unread}
             WHERE user_id_a = #{userIdA} AND user_id_b = #{userIdB}
            """)
    int syncUnreadB(@Param("userIdA") Long userIdA, @Param("userIdB") Long userIdB, @Param("unread") long unread);

    /**
     * 「私信全部已读」时把我在所有会话里 A 侧的未读数清零。
     * <p>
     * 这里可以直接清 0 而不必像 {@link #syncUnreadA} 那样重算：调用方已经在同一个事务里
     * 把 {@code message_record} 里我收到的私信全部标成已读了，实测值必然是 0，
     * 逐段会话去 COUNT 一遍只是把一次批量操作退化成 N 次查询。
     * {@code > 0} 是为了不产生无谓的写和 binlog。
     * </p>
     */
    @Update("""
            UPDATE message_conversation
               SET unread_count_a = 0
             WHERE user_id_a = #{userId} AND unread_count_a > 0
            """)
    int clearUnreadA(@Param("userId") Long userId);

    /** 同 {@link #clearUnreadA}，B 侧 */
    @Update("""
            UPDATE message_conversation
               SET unread_count_b = 0
             WHERE user_id_b = #{userId} AND unread_count_b > 0
            """)
    int clearUnreadB(@Param("userId") Long userId);

    /**
     * 还有未读私信的会话段数。
     * <p>
     * 这里的 OR 躲不掉：归一化存储之后，「我」可能是 A 也可能是 B，
     * 两个分支各查一个索引再合并。一个用户的会话数是几十到几百的量级，不是热点。
     * </p>
     */
    @Select("""
            SELECT COUNT(*)
            FROM message_conversation
            WHERE (user_id_a = #{userId} AND unread_count_a > 0)
               OR (user_id_b = #{userId} AND unread_count_b > 0)
            """)
    long countWithUnread(@Param("userId") Long userId);
}
