package org.tiglor.content.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 上报一次机审结果。
 * <p>
 * 调用方是内容所属的服务（video / interact），不是终端用户——所以
 * {@code (targetType, targetId)} 由请求体给出，而不是从登录态里取。
 * 对应的接口用 {@code content:audit:manage} 权限挡住。
 * </p>
 */
@Data
public class MachineAuditRequest {

    /** 被审对象类型，只认 video / comment / danmaku（大小写不敏感，落库存规范码）；传别的当场报错，不会留下一条查不回来的审核记录 */
    @NotBlank(message = "targetType 不能为空")
    private String targetType;

    /** 被审对象的 id，必填且必须是正数；指向该内容在自己服务那张表里的主键 */
    @NotNull(message = "targetId 不能为空")
    @Positive(message = "targetId 非法")
    private Long targetId;

    /** 风险等级 0-无 1-低 2-中 3-高 */
    @NotNull(message = "riskLevel 不能为空")
    private Integer riskLevel;

    /** 人读的风险标签，可空（纯空白按没填处理），如 {@code porn:0.87}；每次上报整体覆盖上一版 */
    @Size(max = 100, message = "riskLabel 不能超过 100 字")
    private String riskLabel;

    /** 机审原始结果，非空时必须是合法 JSON（列类型是 JSON） */
    private String machineResult;
}
