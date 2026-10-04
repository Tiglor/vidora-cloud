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

    @NotBlank(message = "scene 不能为空")
    private String scene;

    @NotBlank(message = "algoType 不能为空")
    private String algoType;

    @NotBlank(message = "configKey 不能为空")
    @Size(max = 50, message = "configKey 不能超过 50 字")
    private String configKey;

    @Size(max = 500, message = "description 不能超过 500 字")
    private String description;

    /** 非空时必须是合法 JSON，校验在服务层用 Jackson 做 */
    private String configValue;
}
