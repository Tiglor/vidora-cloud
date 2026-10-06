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

    /** {@code message_conversation} 的主键，标识这一段会话 */
    private Long conversationId;
    /** 对面那个人的用户 id——打开这段对话时传的就是它，由服务端按当前登录用户从归一化的 a / b 里挑出来 */
    private Long peerId;

    /** 这一段里「最后一条私信」的 {@code message_record.id} */
    private Long lastMsgId;
    /**
     * 最后一条私信的内容摘要，服务端已掐到固定长度、超出部分以省略号收尾。
     * <p>
     * 那条消息被删除时这里是 null（删掉的内容不该还挂在卡片上），调用方需回退成只显示时间。
     * </p>
     */
    private String lastMsgContent;
    /** 最后一条私信的时间，会话列表按它倒序排 */
    private LocalDateTime lastMsgTime;

    /** 当前登录用户在这一侧的未读数 */
    private Integer unreadCount;
}
