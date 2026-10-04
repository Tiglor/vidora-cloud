package org.tiglor.search.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.tiglor.search.entity.SearchKeywordStat;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface SearchKeywordStatMapper extends BaseMapper<SearchKeywordStat> {

    /**
     * 记一次搜索并维护当天的平均结果数，命中 {@code uk_keyword_date}。
     * <p>
     * <b>两个赋值的顺序不能换。</b>MySQL 的 {@code UPDATE} 子句从左往右求值，
     * 后面的赋值能看到前面已经改过的列。这里 {@code result_count} 必须先算，
     * 用的还是**旧的** {@code search_count}；等它算完 {@code search_count} 才加一。
     * 写成先加一再算平均，分母就多加了一次，均值会越来越偏小。
     * </p>
     * <p>
     * 增量平均 {@code (旧均值 × 旧次数 + 本次结果数) / (旧次数 + 1)}，
     * 这样不必把当天所有单次结果数都存下来。{@code DIV} 是整数除法，直接截断小数——
     * 用 {@code /} 会得到 DECIMAL 再按列类型舍入，行为随 SQL 模式变，不如显式截断。
     * </p>
     */
    @Update("""
            INSERT INTO search_keyword_stat (keyword, stat_date, search_count, result_count)
            VALUES (#{keyword}, #{statDate}, 1, #{resultCount})
            ON DUPLICATE KEY UPDATE
                result_count = (result_count * search_count + #{resultCount}) DIV (search_count + 1),
                search_count = search_count + 1
            """)
    int upsertStat(@Param("keyword") String keyword,
                   @Param("statDate") LocalDate statDate,
                   @Param("resultCount") long resultCount);

    /**
     * 某天里「搜得够多、但还没被收录成建议词」的关键词，按热度倒序。
     * <p>
     * 用 {@code LEFT JOIN ... IS NULL} 而不是先查两张表再在 Java 里做差集：
     * 差集要在应用侧 holding 住一整天的统计行，而这里是纯 SQL 一步到位。
     * 两张表都在 {@code search_service} 库里、都是 {@code utf8mb4_unicode_ci}，
     * 连接比较和 {@code uk_keyword} 的唯一性判定用的是同一套大小写不敏感规则。
     * </p>
     */
    @Select("""
            SELECT s.*
            FROM search_keyword_stat s
            LEFT JOIN search_suggest g ON g.keyword = s.keyword
            WHERE s.stat_date = #{statDate}
              AND s.search_count >= #{minCount}
              AND g.id IS NULL
            ORDER BY s.search_count DESC, s.id ASC
            LIMIT #{limit}
            """)
    List<SearchKeywordStat> mineCandidates(@Param("statDate") LocalDate statDate,
                                           @Param("minCount") long minCount,
                                           @Param("limit") int limit);
}
