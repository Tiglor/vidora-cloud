package org.tiglor.message.dto;

import lombok.Data;

/** {@code message_record} 按 msg_type 分组统计未读数的单行结果。 */
@Data
public class MsgTypeCount {

    /** 分组键：消息类型，1-系统通知 2-互动消息 3-私信 */
    private Integer msgType;
    /** 该类型下「未读且未删除」的条数；某种类型一条未读都没有时不会出现在结果里，取值侧要按 0 兜底 */
    private Long total;
}
