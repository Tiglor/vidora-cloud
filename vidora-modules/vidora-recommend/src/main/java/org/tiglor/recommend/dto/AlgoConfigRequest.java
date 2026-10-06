package org.tiglor.recommend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 写入一条算法配置。
 * <p>
 * {@code (scene, algoType, configKey)} 是唯一键，同一个组合重复提交就是改值，不会多出一行。
 * 没有 {@code status} 字段：启停走 {@code PUT /algo-configs/{id}/status}，
 * 混在 upsert 里的话算法任务每次上报都会把运营手动禁用的配置项重新打开。
 * </p>
 */
@Data
public class AlgoConfigRequest {

    /** 推荐场景，home / follow / topic；大小写不敏感，服务端归一成规范小写值再落库，非法取值当场报 400 */
    @NotBlank(message = "scene 不能为空")
    private String scene;

    /** 算法类型，cf / deep / heatmap；归一规则同 {@code scene}，两者与 {@code configKey} 一起定位唯一一行 */
    @NotBlank(message = "algoType 不能为空")
    private String algoType;

    /** 参数名，两侧空白会被去掉后再参与唯一键比较 */
    @NotBlank(message = "configKey 不能为空")
    @Size(max = 50, message = "configKey 不能超过 50 字")
    private String configKey;

    /** 给人看的说明，可选；空白按 null 存。upsert 会连它一起覆盖，只想改说明也得把 {@code configValue} 一并重传 */
    @Size(max = 500, message = "description 不能超过 500 字")
    private String description;

    /** 非空时必须是合法 JSON，校验在服务层用 Jackson 做 */
    private String configValue;
}
