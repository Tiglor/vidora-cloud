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

    /** 流类型，必填，只认 recommend / hot / follow；服务端会去掉首尾空白并转小写，所以 {@code HOT} 和 {@code hot} 命中的是同一行 */
    @NotBlank(message = "feedType 不能为空")
    private String feedType;

    /** 配置项名，必填（纯空白按没填处理）；和 {@code feedType} 一起决定改的是哪一行 */
    @NotBlank(message = "configKey 不能为空")
    @Size(max = 50, message = "configKey 不能超过 50 字")
    private String configKey;

    /** 人看的说明，可空，只影响管理端表格；不传则把已有说明一并清掉 */
    @Size(max = 500, message = "description 不能超过 500 字")
    private String description;

    /**
     * 配置值，允许为空表示「把这个 key 的值清掉」；非空时必须是合法 JSON
     * （对象、数组或裸数字都行），不合法会在服务层报参数错误，而不是让 MySQL 抛 3140。
     */
    private String configValue;
}
