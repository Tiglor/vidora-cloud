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

    /** 主键，数据库自增；删掉一个配置项再建同名 key 会得到一个新 id */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 流类型：recommend / hot / follow，取值见 {@link org.tiglor.content.enums.FeedType} */
    private String feedType;

    /** 配置项名，与 {@code feedType} 合起来才是那一行（{@code uk_feed_key}），单独看可能撞名 */
    private String configKey;

    /**
     * 配置值，列上是 TEXT 但语义必须是合法 JSON，服务层写入前用 Jackson 解析过一遍；
     * 为 null 表示「这个 key 没配」，它不会出现在 {@code /configs/{feedType}} 的 Map 里，
     * 推荐服务据此回落到代码里的默认参数。
     */
    private String configValue;

    /** 人看的说明，只给管理端表格渲染用，算法侧不读；纯空白按 null 落库 */
    private String description;

    /** 创建时间，插入时自动填充 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 最后更新时间，插入与更新时都自动填充；upsert 改值也会刷新 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
