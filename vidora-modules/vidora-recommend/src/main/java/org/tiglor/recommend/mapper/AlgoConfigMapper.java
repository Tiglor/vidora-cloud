package org.tiglor.recommend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.tiglor.recommend.entity.AlgoConfig;

@Mapper
public interface AlgoConfigMapper extends BaseMapper<AlgoConfig> {

    /**
     * 按 {@code uk_scene_algo_key(scene, algo_type, config_key)} 建或改一条配置。
     * <p>
     * 一条语句完成，不先查再决定：两个人同时改同一个 key 时，先查后插的两条请求
     * 会双双撞到唯一键上，其中一条以 500 收场。
     * </p>
     * <p>
     * <b>不</b>更新 {@code status}：运营把一个配置项禁用之后，算法任务照旧上报新值，
     * 不应该顺手把它重新启用。启停只能走 {@code PUT /algo-configs/{id}/status}。
     * </p>
     * <p>
     * <b>返回值不是行数</b>：插入返回 1、更新返回 2、命中但值没变返回 0。
     * </p>
     */
    @Insert("""
            INSERT INTO recommend_algo_config (scene, algo_type, config_key, config_value, description)
            VALUES (#{scene}, #{algoType}, #{configKey}, #{configValue}, #{description})
            ON DUPLICATE KEY UPDATE
                config_value = VALUES(config_value),
                description  = VALUES(description)
            """)
    int upsert(@Param("scene") String scene,
               @Param("algoType") String algoType,
               @Param("configKey") String configKey,
               @Param("configValue") String configValue,
               @Param("description") String description);
}
