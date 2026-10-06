package org.tiglor.recommend.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 批量写入里的一条推荐候选。
 * <p>
 * {@code userId} 由请求体给出而不是从登录态取：调用方是离线算法任务，
 * 它一次写的是成千上万个用户的候选，不可能以每个用户的身份登录。
 * 因此整个批量接口用 {@code recommend:manage} 权限挡住，不对外开放。
 * </p>
 * <p>
 * 没有 {@code isExposed} / {@code isClicked} 字段——这两个只能由本服务的
 * feed 读取和点击上报来翻，允许写入方指定就等于允许伪造 CTR。
 * </p>
 */
@Data
public class RecommendItemRequest {

    /** 给谁推荐这屏候选，必须是已落库的用户 id，但本服务不校验它存在——填错只会写进一行永远取不到的候选 */
    @NotNull(message = "userId 不能为空")
    @Positive(message = "userId 非法")
    private Long userId;

    /** 候选视频 id，同样不做存在性与上下架状态校验 */
    @NotNull(message = "videoId 不能为空")
    @Positive(message = "videoId 非法")
    private Long videoId;

    /** 所属场景，home / follow / topic；大小写不敏感，服务端归一后才入库，非法取值整批报 400 */
    @NotBlank(message = "scene 不能为空")
    private String scene;

    /** 产出这条候选的算法，cf / deep / heatmap；归一规则同 {@code scene}，命中唯一键时这一列会被新值刷新 */
    @NotBlank(message = "algoType 不能为空")
    private String algoType;

    /** DECIMAL(10,6) 的范围，超界 MySQL 会直接报错 */
    @DecimalMin(value = "0", message = "score 不能为负")
    @DecimalMax(value = "9999.999999", message = "score 超出 DECIMAL(10,6) 范围")
    private BigDecimal score;
}
