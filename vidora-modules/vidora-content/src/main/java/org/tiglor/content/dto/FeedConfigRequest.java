package org.tiglor.content.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 写入一条推荐流配置。
 * <p>
 * {@code (feedType, configKey)} 是唯一键，同一个组合重复提交就是改值，不会多出一行。
 * {@code configValue} 允许为空（表示「删掉这个配置项的值」），但非空时必须是合法 JSON，
 * 校验在服务层用 Jackson 做。
 * </p>
 */
@Data
public class FeedConfigRequest {

    @NotBlank(message = "feedType 不能为空")
    private String feedType;

    @NotBlank(message = "configKey 不能为空")
    @Size(max = 50, message = "configKey 不能超过 50 字")
    private String configKey;

    @Size(max = 500, message = "description 不能超过 500 字")
    private String description;

    private String configValue;
}
