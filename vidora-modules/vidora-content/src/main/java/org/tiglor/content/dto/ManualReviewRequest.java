package org.tiglor.content.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 人工复核结论。
 * <p>
 * 可以覆盖 {@code riskLabel}：机审给的标签往往是 "porn:0.87" 这种，
 * 复核后写成人能看懂的结论对后续复盘更有用。不传就保留机审的标签。
 * </p>
 */
@Data
public class ManualReviewRequest {

    /** 1-放行 2-拦截 */
    @NotNull(message = "manualResult 不能为空")
    private Integer manualResult;

    /** 改写后的风险标签，不传就保留机审给的那个；纯空白等同没传 */
    @Size(max = 100, message = "riskLabel 不能超过 100 字")
    private String riskLabel;
}
