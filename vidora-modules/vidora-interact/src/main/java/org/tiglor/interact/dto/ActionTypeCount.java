package org.tiglor.interact.dto;

import lombok.Data;

/** {@code interact_action} 按 action_type 分组计数的单行结果。 */
@Data
public class ActionTypeCount {

    /** 这一行属于哪种动作，是 GROUP BY 的分组键而不是可写字段 */
    private Integer actionType;

    /**
     * 该动作下的有效行数，即 SQL 里 {@code COUNT(*)} 且只算未取消的记录。
     * <p>分组查询不会为零计数的动作产出行，缺哪一档要由调用方自己按 0 兜底。</p>
     */
    private Long total;
}
