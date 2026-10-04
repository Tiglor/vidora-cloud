package org.tiglor.content.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 推荐流配置，{@code uk_feed_key(feed_type, config_key)} 决定一个流的一个 key 只有一行。
 * <p>
 * {@code configValue} 在 DDL 上是 TEXT 而不是 JSON 类型：值既可能是对象、也可能是数组或裸数字，
 * 用 JSON 列会把「合法的标量」和「非法的字符串」都挡在类型上，排查起来反而更绕。
 * 服务层在写入前用 Jackson 解析一遍，保证读出来的一定是合法 JSON。
 * </p>
 */
@Data
@TableName("content_feed_config")
public class FeedConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 流类型：recommend / hot / follow，取值见 {@link org.tiglor.content.enums.FeedType} */
    private String feedType;

    private String configKey;

    private String configValue;

    private String description;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
