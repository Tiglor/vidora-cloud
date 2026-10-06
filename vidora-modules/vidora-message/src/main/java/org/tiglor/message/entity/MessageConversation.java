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
 * 两个用户之间的私信会话，一行 = 一对用户，{@code uk_conversation(user_id_a, user_id_b)} 保证唯一。
 * <p>
 * <b>写入前必须把 (a, b) 归一化成 (min, max)</b>：唯一键是有序的，
 * 不归一化的话「1 找 2」和「2 找 1」会各建一行，同一段对话被劈成两半，两边各看各的未读数。
 * 归一化之后 {@code unreadCountA} / {@code unreadCountB} 的含义就变成「id 较小的那个人」和
 * 「id 较大的那个人」的未读数，跟「谁是发起方」无关——取值时必须按这个约定换算，
 * 见 {@link org.tiglor.message.service.ConversationService}。
 * </p>
 * <p>
 * 不继承 {@code BaseEntity}：表上没有 {@code is_deleted}。会话是派生数据，
 * 消息全删了会话行留着也无害（未读数为 0，列表按 last_msg_time 排到后面）。
 * </p>
 */
@Data
@TableName("message_conversation")
public class MessageConversation implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 会话主键，数据库自增；不用于业务定位——定位一段会话靠 (userIdA, userIdB) 这个有序唯一键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 两个参与者中 id 较小的那个 */
    private Long userIdA;

    /** 两个参与者中 id 较大的那个 */
    private Long userIdB;

    /**
     * 这一段会话里「最后一条私信」的 {@code message_record.id}，冗余指针。
     * <p>
     * 只推进到 id 较大的那条（SQL 里用 GREATEST 兜住并发）；消息被删除后这里可能指向一行 status=0 的记录，
     * 此时列表接口不再回查出内容。
     * </p>
     */
    private Long lastMsgId;

    /** 最后一条私信的落地时间，会话列表按它倒序排；与 {@link #lastMsgId} 同批写入，新建会话前为空 */
    private LocalDateTime lastMsgTime;

    /** A（id 较小者）的未读数，派生自 {@code message_record.is_read} */
    private Integer unreadCountA;

    /** B（id 较大者）的未读数，派生自 {@code message_record.is_read} */
    private Integer unreadCountB;

    /** 建立这段会话的时间，插入时自动填充，调用方传进来的值不算数 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 最后一次被私信推进或清未读的时间，插入与更新时都自动填充 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
