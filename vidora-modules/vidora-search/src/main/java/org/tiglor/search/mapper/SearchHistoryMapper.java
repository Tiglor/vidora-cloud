package org.tiglor.search.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
import org.tiglor.search.entity.SearchHistory;

@Mapper
public interface SearchHistoryMapper extends BaseMapper<SearchHistory> {

    /**
     * 记一次搜索：命中 {@code uk_user_keyword} 就把次数加一，否则插一行新的。
     * <p>
     * 语句里「不写」 {@code search_count} 的初值以外的任何列：
     * {@code search_count} 靠 DDL 的 {@code DEFAULT 1}，
     * {@code create_time} 和 {@code last_search_time} 靠 {@code DEFAULT CURRENT_TIMESTAMP}，
     * 而 {@code last_search_time} 还带 {@code ON UPDATE CURRENT_TIMESTAMP}，
     * 更新分支不用显式赋值它就会自己往前走。
     * </p>
     * <p>
     * 返回值不是行数：{@code ON DUPLICATE KEY UPDATE} 返回 1=插入、2=更新、0=值没变，
     * 调用方不要拿它当计数用。
     * </p>
     */
    @Update("""
            INSERT INTO search_history (user_id, keyword)
            VALUES (#{userId}, #{keyword})
            ON DUPLICATE KEY UPDATE search_count = search_count + 1
            """)
    int upsertSearch(@Param("userId") Long userId, @Param("keyword") String keyword);
}
