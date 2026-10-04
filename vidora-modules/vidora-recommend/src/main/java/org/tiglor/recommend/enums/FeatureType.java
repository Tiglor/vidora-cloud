package org.tiglor.recommend.enums;

import lombok.Getter;
import org.tiglor.common.core.BizException;
import org.tiglor.common.core.ResultCode;

import java.util.Locale;

/**
 * 用户特征维度，对应 {@code recommend_user_feature.feature_type}。
 * <p>
 * {@code feature_value} 是自由字符串（标签名、分类 id、作者 id），但**类型**必须收敛：
 * 三类特征在召回里的用法完全不同，混在一列里靠 value 猜类型是查不出来的。
 * </p>
 */
@Getter
public enum FeatureType {

    /** 标签偏好，value = 标签名 */
    TAG("tag"),
    /** 分类偏好，value = 分类 id */
    CATEGORY("category"),
    /** 作者偏好，value = 作者用户 id */
    AUTHOR("author");

    private final String code;

    FeatureType(String code) {
        this.code = code;
    }

    public static FeatureType of(String code) {
        if (code != null) {
            String normalized = code.trim().toLowerCase(Locale.ROOT);
            for (FeatureType type : values()) {
                if (type.code.equals(normalized)) {
                    return type;
                }
            }
        }
        throw new BizException(ResultCode.VALIDATE_FAILED, "未知的特征类型：" + code + "，可选 tag / category / author");
    }
}
