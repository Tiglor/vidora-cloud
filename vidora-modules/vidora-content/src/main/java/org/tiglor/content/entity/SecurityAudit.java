package org.tiglor.content.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 内容安全审核结果，{@code uk_target(target_type, target_id)} 决定一个对象只有一行审核记录。
 * <p>
 * 一个对象一行、而不是「一次审核一行」：调用方要回答的问题永远是「这条内容现在能不能放出去」，
 * 留历史版本的话每次都得再挑一遍哪条算数。机审复跑会「覆盖」上一版结果，
 * 并把 {@code manualResult} 重置回 NULL——人工当初放行的是那一版内容评分，
 * 分数变了旧结论就不该继续生效。
 * </p>
 */
@Data
@TableName("content_security_audit")
public class SecurityAudit implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键，数据库自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 对象类型：video / comment / danmaku，取值见 {@link org.tiglor.content.enums.AuditTargetType} */
    private String targetType;

    /** 被审对象的 id，指向 {@code targetType} 那一类内容自己那张表；与它合起来才是这一行（{@code uk_target}） */
    private Long targetId;

    /** 风险等级：0-无 1-低 2-中 3-高，见 {@link org.tiglor.content.enums.RiskLevel} */
    private Integer riskLevel;

    /** 人读的风险标签，机审给的往往是 {@code porn:0.87} 这类原文，人工复核时可以覆盖成人能看懂的结论；不传保留原值 */
    private String riskLabel;

    /** 机审原始结果，DDL 上是 JSON 列，写进去的必须是合法 JSON */
    private String machineResult;

    /** 人工复核：NULL-未复核 1-放行 2-拦截，见 {@link org.tiglor.content.enums.ManualResult} */
    private Integer manualResult;

    /** 创建时间，插入时自动填充；表上没有 update_time，所以复跑覆盖掉旧结论之后看不出上一版是何时 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
