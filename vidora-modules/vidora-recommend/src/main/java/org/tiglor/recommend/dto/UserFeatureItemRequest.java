package org.tiglor.recommend.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 批量写入里的一条用户特征。
 * <p>
 * 没有 {@code userId}：整批同属一个用户，写在 {@link UserFeatureBatchRequest} 上。
 * 特征任务本来就是按人算画像的，让每条自带 userId 只会多出一列冗余，
 * 还允许一批里混进别人的数据。
 * </p>
 */
@Data
public class UserFeatureItemRequest {

    /** 特征维度，tag / category / author；大小写不敏感，服务端归一成规范小写值再入库，非法取值整批报 400 */
    @NotBlank(message = "featureType 不能为空")
    private String featureType;

    /** 特征值，含义由 {@code featureType} 决定（标签名 / 分类 id / 作者用户 id）；入库前去掉两侧空白，它和类型一起构成唯一键所以不会自动清理历史写法 */
    @NotBlank(message = "featureValue 不能为空")
    @Size(max = 100, message = "featureValue 不能超过 100 字")
    private String featureValue;

    /** DECIMAL(6,4) 的范围。超界直接拒，不静默截断——那会把模型算错的信号藏起来 */
    @DecimalMin(value = "0", message = "weight 不能为负")
    @DecimalMax(value = "99.9999", message = "weight 超出 DECIMAL(6,4) 范围")
    private BigDecimal weight;
}
