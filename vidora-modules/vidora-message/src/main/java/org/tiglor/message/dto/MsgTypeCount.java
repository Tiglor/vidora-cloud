package org.tiglor.message.dto;

import lombok.Data;

/** {@code message_record} 按 msg_type 分组统计未读数的单行结果。 */
@Data
public class MsgTypeCount {

    private Integer msgType;
    private Long total;
}
