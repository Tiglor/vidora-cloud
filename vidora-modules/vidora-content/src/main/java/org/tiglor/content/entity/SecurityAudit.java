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
 * 留历史版本的话每次都得再挑一遍哪条算数。机审复跑会**覆盖**上一版结果，
 * 并把 {@code manualResult} 重置回 NULL——人工当初放行的是那一版内容评分，
 * 分数变了旧结论就不该继续生效。
 * </p>
 */
@Data
@TableName("content_security_audit")
public class SecurityAudit implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 对象类型：video / comment / danmaku，取值见 {@link org.tiglor.content.enums.AuditTargetType} */
    private String targetType;

    private Long targetId;

    /** 风险等级：0-无 1-低 2-中 3-高，见 {@link org.tiglor.content.enums.RiskLevel} */
    private Integer riskLevel;

    private String riskLabel;

    /** 机审原始结果，DDL 上是 JSON 列，写进去的必须是合法 JSON */
    private String machineResult;

    /** 人工复核：NULL-未复核 1-放行 2-拦截，见 {@link org.tiglor.content.enums.ManualResult} */
    private Integer manualResult;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
