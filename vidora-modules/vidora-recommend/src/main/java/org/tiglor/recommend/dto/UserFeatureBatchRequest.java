package org.tiglor.recommend.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 一次写入一个用户的画像。
 * <p>
 * 按用户分批而不是一次提交全量：失败时只影响这一个人的画像，
 * 而不是让算法任务因为一条脏数据回滚掉几百万行。
 * </p>
 */
@Data
public class UserFeatureBatchRequest {

    /** 单个用户的特征条数上限。一个人有几千个标签偏好本身就是异常信号 */
    public static final int MAX_ITEMS = 1000;

    /** 这一批画像属于谁；服务层还会再验一次非正数，所以填 0 或负数是当场报 400 而不是写进一行脏数据 */
    @NotNull(message = "userId 不能为空")
    @Positive(message = "userId 非法")
    private Long userId;

    /** 这个人的完整画像条目，整批共用上面的 {@code userId}；写入按 {@code (featureType, featureValue)} 覆盖权重，不包事务，中途失败可重跑整批 */
    @NotEmpty(message = "items 不能为空")
    @Size(max = MAX_ITEMS, message = "单个用户最多 " + MAX_ITEMS + " 条特征，请分批提交")
    @Valid
    private List<UserFeatureItemRequest> items;
}
