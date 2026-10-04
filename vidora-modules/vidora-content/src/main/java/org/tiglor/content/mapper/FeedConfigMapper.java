package org.tiglor.content.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.tiglor.content.entity.FeedConfig;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

@Mapper
public interface FeedConfigMapper extends BaseMapper<FeedConfig> {

    /**
     * 按 {@code uk_feed_key(feed_type, config_key)} 建或改一条配置。
     * <p>
     * 一条语句完成，不先查再决定：两个运营同时改同一个 key 时，先查后插的两条请求
     * 会双双撞到唯一键上，其中一条以 500 收场。
     * </p>
     * <p>
     * <b>返回值不是行数</b>：MySQL 对 {@code ON DUPLICATE KEY UPDATE} 的约定是
     * 插入返回 1、更新返回 2、命中但值没变返回 0。
     * </p>
     */
    @Insert("""
            INSERT INTO content_feed_config (feed_type, config_key, config_value, description)
            VALUES (#{feedType}, #{configKey}, #{configValue}, #{description})
            ON DUPLICATE KEY UPDATE
                config_value = VALUES(config_value),
                description  = VALUES(description)
            """)
    int upsert(@Param("feedType") String feedType,
               @Param("configKey") String configKey,
               @Param("configValue") String configValue,
               @Param("description") String description);
}
