package org.tiglor.recommend.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 用户行为特征，供外部召回模型读取。
 * <p>
 * {@code uk_user_feature(user_id, feature_type, feature_value)} 决定写入是**覆盖**语义：
 * 特征任务每次重算一个人的完整画像，同一个 (类型, 值) 只保留最新权重。
 * 累加会让权重单调增长，几周之后所有值都顶到列上限。
 * </p>
 * <p>
 * {@code weight} 是 DECIMAL(6,4)，上限 99.9999。MySQL 严格模式下超界直接报错，
 * 非严格模式静默截断成 99.9999——两种结果都很难查，所以
 * {@code UserFeatureItemRequest} 在 DTO 层就用 {@code @DecimalMax} 拒掉，报 400 并带上字段名。
 * </p>
 * <p>
 * 表上只有 {@code update_time} 没有 {@code create_time}：能知道某个偏好最后一次被强化是什么时候，
 * 但不知道它是什么时候出现的。做「新兴趣 vs 长期兴趣」区分时需要补列。
 * </p>
 */
@Data
@TableName("recommend_user_feature")
public class UserFeature implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** 特征类型，取值见 {@link org.tiglor.recommend.enums.FeatureType} */
    private String featureType;

    /** 特征值：标签名 / 分类 id / 作者 id，具体含义由 featureType 决定 */
    private String featureValue;

    private BigDecimal weight;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
