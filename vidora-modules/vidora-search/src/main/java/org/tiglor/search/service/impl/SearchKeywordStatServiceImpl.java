package org.tiglor.search.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.tiglor.search.entity.SearchKeywordStat;
import org.tiglor.search.mapper.SearchKeywordStatMapper;
import org.tiglor.search.service.SearchKeywordStatService;

import java.time.LocalDate;
import java.util.List;

/**
 * 搜索词统计的读取。
 * <p>
 * 写入只发生在 {@code SearchHistoryServiceImpl.record} 里的那一条 upsert，
 * 这里刻意不暴露任何修改计数的方法。
 * </p>
 */
@Service
public class SearchKeywordStatServiceImpl extends ServiceImpl<SearchKeywordStatMapper, SearchKeywordStat>
        implements SearchKeywordStatService {

    private static final long MAX_PAGE_SIZE = 100L;

    @Override
    public List<SearchKeywordStat> hot(LocalDate date, int limit) {
        int max = (int) Math.min(Math.max(limit, 1), MAX_HOT_LIMIT);
        // 命中 idx_stat_date_count(stat_date, search_count)，过滤和排序都在索引里，不用回表排序
        Page<SearchKeywordStat> page = new Page<>(1, max, false);
        lambdaQuery()
                .eq(SearchKeywordStat::getStatDate, date == null ? LocalDate.now() : date)
                .orderByDesc(SearchKeywordStat::getSearchCount)
                // id 是兜底排序键：搜索次数相同的词没有稳定顺序的话，刷新两次榜单会给出不同的排列
                .orderByAsc(SearchKeywordStat::getId)
                .page(page);
        return page.getRecords();
    }

    @Override
    public Page<SearchKeywordStat> page(long current, long size, LocalDate date, String keyword) {
        String fuzzy = trimToNull(keyword);
        return lambdaQuery()
                .eq(date != null, SearchKeywordStat::getStatDate, date)
                .like(fuzzy != null, SearchKeywordStat::getKeyword, fuzzy)
                .orderByDesc(SearchKeywordStat::getStatDate)
                .orderByDesc(SearchKeywordStat::getSearchCount)
                .orderByAsc(SearchKeywordStat::getId)
                .page(new Page<>(Math.max(current, 1), clampSize(size)));
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static long clampSize(long size) {
        return Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
    }
}
