package org.tiglor.message.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.tiglor.message.dto.MsgTypeCount;
import org.tiglor.message.entity.MessageRecord;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface MessageRecordMapper extends BaseMapper<MessageRecord> {

    /**
     * 按类型统计某人的未读数，一次查询出三个红点。
     * <p>
     * 走 {@code idx_receiver_type(receiver_id, msg_type, is_read)}：三列全在索引里，
     * 是一次覆盖索引扫描；GROUP BY 的列正好是索引第二列，也不用额外排序。
     * </p>
     * <p>
     * 已删除（status=0）的消息不算未读——用户删掉的通知不该继续顶着红点。
     * status 不在这个索引里，这一条要回表过滤；未读消息本身是少数，代价可接受。
     * </p>
     */
    @Select("""
            SELECT msg_type AS msgType, COUNT(*) AS total
            FROM message_record
            WHERE receiver_id = #{receiverId} AND is_read = 0 AND status = 1
            GROUP BY msg_type
            """)
    List<MsgTypeCount> countUnreadByType(@Param("receiverId") Long receiverId);

    /**
     * 某人从某个发件人那里收到的、还没读的私信数。
     * <p>
     * 用于「标记会话已读」之后**重算**会话表的未读数，而不是直接把它清成 0，
     * 见 {@code ConversationServiceImpl#markRead}。
     * </p>
     */
    @Select("""
            SELECT COUNT(*)
            FROM message_record
            WHERE receiver_id = #{receiverId} AND sender_id = #{senderId}
              AND msg_type = 3 AND is_read = 0 AND status = 1
            """)
    long countUnreadFrom(@Param("receiverId") Long receiverId, @Param("senderId") Long senderId);

    /**
     * 把收件箱里符合条件的未读消息一次性标记为已读，返回影响行数。
     * <p>
     * {@code is_read = 0} 写进 WHERE：重复的「全部已读」第二次影响 0 行，
     * 不会把 read_time 刷成新值。{@code senderId} 为 null 时不限制发件人
     * （整类消息全标已读），非 null 时只标某一段私信会话。
     * </p>
     */
    @Update("""
            <script>
            UPDATE message_record
               SET is_read = 1, read_time = #{readTime}
             WHERE receiver_id = #{receiverId}
               AND msg_type = #{msgType}
               AND is_read = 0
               AND status = 1
               <if test="senderId != null">AND sender_id = #{senderId}</if>
            </script>
            """)
    int markRead(@Param("receiverId") Long receiverId,
                 @Param("msgType") int msgType,
                 @Param("senderId") Long senderId,
                 @Param("readTime") LocalDateTime readTime);
}
