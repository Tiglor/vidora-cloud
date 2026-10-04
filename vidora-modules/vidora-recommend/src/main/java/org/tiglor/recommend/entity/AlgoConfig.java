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

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 推荐场景，取值见 {@link org.tiglor.recommend.enums.RecommendScene} */
    private String scene;

    /** 算法类型，取值见 {@link org.tiglor.recommend.enums.AlgoType} */
    private String algoType;

    private String configKey;

    private String configValue;

    private String description;

    /** 状态：0-禁用 1-启用。禁用是「让算法回落到默认参数」，不是删除 */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
