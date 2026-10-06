package org.tiglor.search.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 回报一次搜索。
 * <p>
 * 搜索本身还没落地（Elasticsearch 是独立的待完成项），所以现在由调用方——前端或者将来的 BFF——
 * 在拿到结果之后回报关键词和结果数，本服务只负责记账。等 ES 接上，
 * 这次回报会变成检索接口内部的一步，对外不再有单独的上报入口。
 * </p>
 * <p>
 * {@code resultCount} 允许不传，按 0 计。它是「这次搜到了几条」，
 * 用来算 {@code search_keyword_stat.result_count} 这个平均值：
 * 搜的人多、结果数常年是 0 的词就是内容缺口。
 * </p>
 */
@Data
public class SearchRecordRequest {

    /** 关键词。VARCHAR(200) 是**字符**数，{@code @Size} 数的是 UTF-16 码元，对 emoji 更严一点，方向是对的 */
    @NotBlank(message = "keyword 不能为空")
    @Size(max = 200, message = "keyword 不能超过 200 字")
    private String keyword;

    /**
     * 这次搜索命中多少条结果，不传按 0 计，负数会被拒。
     * <p>要报的是命中总数而不是当页条数：一次回报只往当天的均值里加一个样本，
     * 报分页尺寸会让所有翻页调用方把均值越拉越低，也就看不出「搜得多但搜不到东西」的词了。</p>
     */
    @PositiveOrZero(message = "resultCount 不能为负")
    private Long resultCount;
}
