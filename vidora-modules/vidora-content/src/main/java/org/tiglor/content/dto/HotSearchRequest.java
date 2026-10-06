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

    /** 上榜词，必填（纯空白按没填处理）；与 {@code rankDate} 一起决定这次是新增还是覆盖已有那一行 */
    @NotBlank(message = "keyword 不能为空")
    @Size(max = 100, message = "keyword 不能超过 100 字")
    private String keyword;

    /** 热度分，重排名次时的主排序键；这里是整体覆盖而不是累加，不传等于把它改成 0 */
    @PositiveOrZero(message = "heatScore 不能为负")
    private Integer heatScore;

    /** 搜索次数，热度相同时的重排次排序键；同样是覆盖不是累加，本模块不统计真实搜索量 */
    @PositiveOrZero(message = "searchCount 不能为负")
    private Long searchCount;

    /** 榜单日期，不传按今天 */
    private LocalDate rankDate;

    /** 1-上线 0-下线，不传按上线 */
    private Integer status;
}
