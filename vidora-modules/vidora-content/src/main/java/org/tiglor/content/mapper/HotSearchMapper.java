package org.tiglor.content.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.tiglor.content.entity.HotSearch;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import java.time.LocalDate;

@Mapper
public interface HotSearchMapper extends BaseMapper<HotSearch> {

    /**
     * 按 {@code uk_keyword_date(keyword, rank_date)} 建或改一个上榜词。
     * <p>
     * 命中已有行时**不更新 {@code rank}**：排名是 {@code HotSearchService#rebuild} 按整榜热度
     * 算出来的相对位置，让单个词的写入去改它，会把别的词挤到重复的排名上。
     * 新行的 rank 先给 0，等重算时统一分配。
     * </p>
     * <p>
     * {@code rank} 是 MySQL 8 保留字，列名必须带反引号。
     * </p>
     * <p>
     * 返回值同 {@code ON DUPLICATE KEY UPDATE} 的约定：1-插入 2-更新 0-没变，不是行数。
     * </p>
     */
    @Insert("""
            INSERT INTO content_hot_search (keyword, heat_score, `rank`, search_count, status, rank_date)
            VALUES (#{keyword}, #{heatScore}, 0, #{searchCount}, #{status}, #{rankDate})
            ON DUPLICATE KEY UPDATE
                heat_score   = VALUES(heat_score),
                search_count = VALUES(search_count),
                status       = VALUES(status)
            """)
    int upsert(@Param("keyword") String keyword,
               @Param("heatScore") int heatScore,
               @Param("searchCount") long searchCount,
               @Param("status") int status,
               @Param("rankDate") LocalDate rankDate);
}
