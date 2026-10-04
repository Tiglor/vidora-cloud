package org.tiglor.interact.dto;

import lombok.Data;

/** {@code interact_action} 按 action_type 分组计数的单行结果。 */
@Data
public class ActionTypeCount {

    private Integer actionType;
    private Long total;
}
