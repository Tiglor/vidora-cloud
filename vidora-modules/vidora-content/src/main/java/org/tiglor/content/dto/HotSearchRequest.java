package org.tiglor.content.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

/**
 * 往榜单里加一个词或刷新它的热度。
 * <p>
 * 没有 {@code rank} 字段：排名是整榜按热度算出来的相对位置，接受外部指定就会出现两个词并列第 3。
 * 写完调 {@code POST /hot-searches/rebuild} 重排。
 * </p>
 */
@Data
public class HotSearchRequest {

    @NotBlank(message = "keyword 不能为空")
    @Size(max = 100, message = "keyword 不能超过 100 字")
    private String keyword;

    @PositiveOrZero(message = "heatScore 不能为负")
    private Integer heatScore;

    @PositiveOrZero(message = "searchCount 不能为负")
    private Long searchCount;

    /** 榜单日期，不传按今天 */
    private LocalDate rankDate;

    /** 1-上线 0-下线，不传按上线 */
    private Integer status;
}
