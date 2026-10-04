package org.tiglor.recommend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.tiglor.recommend.entity.UserFeature;

import java.util.List;

@Mapper
public interface UserFeatureMapper extends BaseMapper<UserFeature> {

    /**
     * 特征任务批量写入一个用户的画像。
     * <p>
     * 撞 {@code uk_user_feature} 时<b>覆盖</b>权重而不是累加：任务每次重算的是完整画像，
     * 累加会让权重单调增长，几周之后所有值都顶到 DECIMAL(6,4) 的上限，画像退化成一片相同的数。
     * </p>
     * <p>
     * {@code update_time} 不在列清单里，交给 DDL 的
     * {@code DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP} 维护。
     * </p>
     * <p>
     * <b>返回值不是行数</b>，同 {@code RecommendResultMapper.batchUpsert}。
     * </p>
     */
    @Insert("""
            <script>
            INSERT INTO recommend_user_feature (user_id, feature_type, feature_value, weight)
            VALUES
            <foreach collection="items" item="it" separator=",">
                (#{it.userId}, #{it.featureType}, #{it.featureValue}, #{it.weight})
            </foreach>
            ON DUPLICATE KEY UPDATE
                weight = VALUES(weight)
            </script>
            """)
    int batchUpsert(@Param("items") List<UserFeature> items);
}
