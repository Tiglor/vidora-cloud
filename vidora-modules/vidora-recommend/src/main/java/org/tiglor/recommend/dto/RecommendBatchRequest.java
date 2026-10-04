package org.tiglor.recommend.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 一次批量写入。
 * <p>
 * 上限 {@code MAX_ITEMS} 是**请求体**层面的限制，不是 SQL 层面的：
 * 服务层还会按 {@code BATCH_CHUNK} 再切成多条 INSERT，避免单条语句超过
 * {@code max_allowed_packet}。两道限制解决的是不同的问题。
 * </p>
 * <p>
 * 请求体上限的意义在于挡住「一次 POST 塞十万条」——那种请求光是反序列化就能把
 * 堆打满，而且失败时整批回滚，算法任务侧什么也拿不到。
 * </p>
 */
@Data
public class RecommendBatchRequest {

    /** 单次请求最多接受的条数 */
    public static final int MAX_ITEMS = 5000;

    @NotEmpty(message = "items 不能为空")
    @Size(max = MAX_ITEMS, message = "单次最多写入 " + MAX_ITEMS + " 条，请分批提交")
    @Valid
    private List<RecommendItemRequest> items;
}
