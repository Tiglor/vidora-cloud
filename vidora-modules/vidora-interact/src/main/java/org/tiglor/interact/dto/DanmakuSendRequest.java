package org.tiglor.interact.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/** 发送弹幕。样式字段可省略，服务端补默认值。 */
@Data
public class DanmakuSendRequest {

    /** 发到哪条视频的弹幕时间线上；服务端不校验这条视频是否存在或是否已发布 */
    @NotNull(message = "videoId 不能为空")
    private Long videoId;

    /** 弹幕文本，落库前 trim——只发空格等于发空内容，会被非空校验挡下 */
    @NotBlank(message = "弹幕内容不能为空")
    @Size(max = 500, message = "弹幕内容不能超过 500 字")
    private String content;

    /** 弹幕出现的时间点（秒）。用 BigDecimal 是因为 float 的累积误差会让同一时间点的弹幕排序抖动 */
    @NotNull(message = "appearTime 不能为空")
    @DecimalMin(value = "0", message = "appearTime 不能为负")
    @DecimalMax(value = "86400", message = "appearTime 超出合理范围")
    private BigDecimal appearTime;

    /** 可省略，服务端补白色；入库前统一转成大写十六进制，所以传 #ffffff 也会存成 #FFFFFF */
    @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "color 必须是 #RRGGBB 形式")
    private String color;

    /** 可省略，服务端补默认字号 25 */
    @Min(value = 12, message = "fontSize 最小 12")
    @Max(value = 48, message = "fontSize 最大 48")
    private Integer fontSize;

    /** 位置：0-滚动 1-顶部 2-底部 */
    @Min(value = 0, message = "position 只能是 0/1/2")
    @Max(value = 2, message = "position 只能是 0/1/2")
    private Integer position;
}
