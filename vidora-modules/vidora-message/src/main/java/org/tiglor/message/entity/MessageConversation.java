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

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 两个参与者中 id 较小的那个 */
    private Long userIdA;

    /** 两个参与者中 id 较大的那个 */
    private Long userIdB;

    private Long lastMsgId;

    private LocalDateTime lastMsgTime;

    /** A（id 较小者）的未读数，派生自 {@code message_record.is_read} */
    private Integer unreadCountA;

    /** B（id 较大者）的未读数，派生自 {@code message_record.is_read} */
    private Integer unreadCountB;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
