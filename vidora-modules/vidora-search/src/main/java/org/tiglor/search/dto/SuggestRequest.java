package org.tiglor.search.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 新增或修改一条建议词。
 * <p>
 * 没有 {@code status} 字段：启停走 {@code PUT /suggests/{id}/status}。
 * 和推荐算法配置那边是同一个道理——新增的词默认启用，
 * 而「把一个已经被运营禁用的词顺手改回启用」不该是改权重时的副作用。
 * </p>
 * <p>
 * {@code source} 不传按人工（1）算。挖掘接口不走这个 DTO，它固定写 2。
 * </p>
 */
@Data
public class SuggestRequest {

    /**
     * 建议词，入库前去掉首尾空白。全表唯一（大小写不敏感），
     * 所以新增撞词、或把另一个词改成已有词，都会被当成校验失败挡回来；改自己不算撞词。
     */
    @NotBlank(message = "keyword 不能为空")
    @Size(max = 200, message = "keyword 不能超过 200 字")
    private String keyword;

    /** 只影响排序，不参与计算，量级由运营自己定 */
    @PositiveOrZero(message = "weight 不能为负")
    private Integer weight;

    /** 1-人工 2-自动挖掘，取值见 {@link org.tiglor.search.enums.SuggestSource} */
    private Integer source;
}
