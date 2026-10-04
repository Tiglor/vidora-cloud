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

    @NotNull(message = "userId 不能为空")
    @Positive(message = "userId 非法")
    private Long userId;

    @NotEmpty(message = "items 不能为空")
    @Size(max = MAX_ITEMS, message = "单个用户最多 " + MAX_ITEMS + " 条特征，请分批提交")
    @Valid
    private List<UserFeatureItemRequest> items;
}
