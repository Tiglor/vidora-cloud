package org.tiglor.recommend.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 推荐算法配置，{@code (scene, algo_type, config_key)} 唯一。
 * <p>
 * 算法本身在外部训练和运行，这张表只是它读取参数的地方——所以本服务不提供任何
 * 「算出推荐结果」的能力，只负责存、取、启停。
 * </p>
 * <p>
 * {@code config_value} 用 TEXT 不用 JSON 列：配置值经常是 {@code 0.35}、{@code true}
 * 这种裸标量，JSON 列虽然收得下但读出来带引号，调用方还得再解一层。
 * 合法性由服务层用 Jackson 校验。
 * </p>
 */
@Data
@TableName("recommend_algo_config")
public class AlgoConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键，数据库自增。配置行靠 {@code uk_scene_algo_key}（scene + algoType + configKey）定位，调用方传进来的 id 不参与写入；这张表没有 {@code is_deleted}，删除是物理删除 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 推荐场景，取值见 {@link org.tiglor.recommend.enums.RecommendScene} */
    private String scene;

    /** 算法类型，取值见 {@link org.tiglor.recommend.enums.AlgoType} */
    private String algoType;

    /** 参数名，入参两侧空白会被去掉；它和 {@code (scene, algoType)} 一起构成唯一键，所以重复提交是改值不是加行 */
    private String configKey;

    /**
     * 配置值，约定是一段 JSON——裸标量 {@code 0.35}、{@code true} 同样合法。
     * <p>
     * 写入前用 Jackson 校验，非法值报 400 并带上字段名，而不是让 MySQL 的错误穿过全局异常处理器变成一句「服务异常」。
     * 传空白等于把值清掉（列允许 NULL）；{@code GET /algo-configs/configs} 的扁平视图会跳过 null 项，
     * 对算法侧来说「没配」和「配了个 null」没有区别。
     * </p>
     */
    private String configValue;

    /** 给人看的说明，管理端列表用；空白按 null 存。upsert 会连它一起覆盖，只想改说明也得把值一并重传 */
    private String description;

    /** 状态：0-禁用 1-启用。禁用是「让算法回落到默认参数」，不是删除 */
    private Integer status;

    /** 建行时刻。upsert 的列清单里没有这一列，也不走 MyBatis-Plus 的实体插入，所以这个填充注解没有触发点，实际由 DDL 的 {@code DEFAULT CURRENT_TIMESTAMP} 给值 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 最后修改时刻，由 DDL 的 {@code ON UPDATE CURRENT_TIMESTAMP} 刷新；本模块没有走实体更新的写路径（只有 upsert 与删除），所以 {@code INSERT_UPDATE} 填充在这里同样不生效 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
