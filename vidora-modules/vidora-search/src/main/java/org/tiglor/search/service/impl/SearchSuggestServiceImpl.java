package org.tiglor.search.service.impl;

import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.tiglor.common.core.BizException;
import org.tiglor.common.core.ResultCode;
import org.tiglor.common.redis.CacheNames;
import org.tiglor.search.dto.SuggestRequest;
import org.tiglor.search.entity.SearchKeywordStat;
import org.tiglor.search.entity.SearchSuggest;
import org.tiglor.search.enums.SuggestSource;
import org.tiglor.search.mapper.SearchKeywordStatMapper;
import org.tiglor.search.mapper.SearchSuggestMapper;
import org.tiglor.search.service.SearchSuggestService;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 搜索建议词。
 * <p>
 * 缓存的只有 {@link #top}（key = limit，最多 {@code MAX_LIMIT} 条），
 * 带前缀的联想查询一律直接查库——前缀是用户输入的任意字符串，
 * 拿它当缓存 key 等于把 Redis 的条目数交给了外部。
 * 好在 {@code likeRight} 生成的是 {@code keyword LIKE 'xx%'}，能走 {@code uk_keyword} 的前缀范围扫描。
 * </p>
 */
@Service
@RequiredArgsConstructor
public class SearchSuggestServiceImpl extends ServiceImpl<SearchSuggestMapper, SearchSuggest>
        implements SearchSuggestService {

    /** 一次挖掘最多收录多少个词，同时也是单条 INSERT 的行数上限（{@code max_allowed_packet}） */
    public static final int MAX_MINE_LIMIT = 200;

    /**
     * 挖掘出来的词的初始权重。
     * <p>
     * 固定 0，不用它的搜索次数：次数可能上万，直接当权重会让一次挖掘把运营手工排好的
     * 联想框整个顶掉。0 意味着这些词能被前缀匹配到，但在 {@link #top} 里排在所有人工词之后，
     * 等运营看过再逐个提权重。
     * </p>
     * <p>
     * 挖出来的词相互之间的热度顺序不会丢：候选是按 {@code search_count DESC} 取的，
     * 插入后 id 递增，而 {@code top} 的兜底排序键是 {@code id ASC}，同权重下正好还原热度序。
     * </p>
     */
    private static final int MINED_WEIGHT = 0;

    private static final long MAX_PAGE_SIZE = 100L;

    private final SearchKeywordStatMapper statMapper;

    @Override
    // 命中 idx_status_weight(status, weight)，过滤和排序都在索引里
    @Cacheable(cacheNames = CacheNames.SEARCH_SUGGEST_TOP, key = "#limit")
    public List<SearchSuggest> top(int limit) {
        return enabled()
                .orderByDesc(SearchSuggest::getWeight)
                .orderByAsc(SearchSuggest::getId)
                .page(new Page<>(1, clampLimit(limit), false))
                .getRecords();
    }

    @Override
    public List<SearchSuggest> suggest(String prefix, int limit) {
        String normalized = trimToNull(prefix);
        if (normalized == null) {
            // 空前缀就是「列出全部建议词」，那是 top 的活；放任它走 LIKE '%' 会白白全表扫一遍
            return List.of();
        }
        return enabled()
                .likeRight(SearchSuggest::getKeyword, normalized)
                .orderByDesc(SearchSuggest::getWeight)
                .orderByAsc(SearchSuggest::getId)
                .page(new Page<>(1, clampLimit(limit), false))
                .getRecords();
    }

    @Override
    public Page<SearchSuggest> page(long current, long size, String keyword, Integer status, Integer source) {
        String fuzzy = trimToNull(keyword);
        Integer sourceCode = source == null ? null : SuggestSource.of(source).getCode();
        return lambdaQuery()
                .like(fuzzy != null, SearchSuggest::getKeyword, fuzzy)
                .eq(status != null, SearchSuggest::getStatus, status)
                .eq(sourceCode != null, SearchSuggest::getSource, sourceCode)
                .orderByDesc(SearchSuggest::getWeight)
                .orderByAsc(SearchSuggest::getId)
                .page(new Page<>(Math.max(current, 1), clampSize(size)));
    }

    @Override
    @CacheEvict(cacheNames = CacheNames.SEARCH_SUGGEST_TOP, allEntries = true)
    public SearchSuggest create(SuggestRequest request) {
        String keyword = normalizeKeyword(request.getKeyword());
        // uk_keyword 建在 _ci 排序规则的列上，等值比较同样大小写不敏感，和库里的唯一性判定一致
        if (lambdaQuery().eq(SearchSuggest::getKeyword, keyword).count() > 0) {
            throw new BizException(ResultCode.VALIDATE_FAILED, "建议词「" + keyword + "」已经存在");
        }
        SearchSuggest row = new SearchSuggest();
        row.setKeyword(keyword);
        row.setWeight(request.getWeight() == null ? 0 : request.getWeight());
        row.setSource(sourceOrDefault(request.getSource()).getCode());
        row.setStatus(SearchSuggest.STATUS_ENABLED);
        save(row);
        return row;
    }

    @Override
    @CacheEvict(cacheNames = CacheNames.SEARCH_SUGGEST_TOP, allEntries = true)
    public SearchSuggest update(Long id, SuggestRequest request) {
        SearchSuggest existing = requireExists(id);
        String keyword = normalizeKeyword(request.getKeyword());
        // 改名要排掉自己，否则「只改权重、词不动」这种最常见的操作会撞自己那一行
        if (lambdaQuery().eq(SearchSuggest::getKeyword, keyword).ne(SearchSuggest::getId, id).count() > 0) {
            throw new BizException(ResultCode.VALIDATE_FAILED, "建议词「" + keyword + "」已经存在");
        }
        existing.setKeyword(keyword);
        existing.setWeight(request.getWeight() == null ? 0 : request.getWeight());
        existing.setSource(sourceOrDefault(request.getSource()).getCode());
        // 刻意不动 status：把一个已经被运营禁用的词改回启用，不该是「改权重」的副作用
        updateById(existing);
        return existing;
    }

    @Override
    @CacheEvict(cacheNames = CacheNames.SEARCH_SUGGEST_TOP, allEntries = true)
    public void setStatus(Long id, int status) {
        if (status != SearchSuggest.STATUS_DISABLED && status != SearchSuggest.STATUS_ENABLED) {
            throw new BizException(ResultCode.VALIDATE_FAILED, "status 只能是 0-禁用 或 1-启用");
        }
        requireExists(id);
        // ne(status) 让「已经是目标值」的情况影响 0 行，重复点击是幂等的
        lambdaUpdate()
                .set(SearchSuggest::getStatus, status)
                .eq(SearchSuggest::getId, id)
                .ne(SearchSuggest::getStatus, status)
                .update();
    }

    @Override
    @CacheEvict(cacheNames = CacheNames.SEARCH_SUGGEST_TOP, allEntries = true)
    public void delete(Long id) {
        requireExists(id);
        removeById(id);
    }

    @Override
    @CacheEvict(cacheNames = CacheNames.SEARCH_SUGGEST_TOP, allEntries = true)
    public int mine(LocalDate date, long minCount, int limit) {
        if (minCount < 1) {
            throw new BizException(ResultCode.VALIDATE_FAILED, "minCount 至少是 1，否则会把只被搜过一次的词全挖进来");
        }
        int max = (int) Math.min(Math.max(limit, 1), MAX_MINE_LIMIT);
        List<SearchKeywordStat> candidates =
                statMapper.mineCandidates(date == null ? LocalDate.now() : date, minCount, max);
        if (candidates.isEmpty()) {
            return 0;
        }
        List<SearchSuggest> rows = new ArrayList<>(candidates.size());
        for (SearchKeywordStat candidate : candidates) {
            SearchSuggest row = new SearchSuggest();
            row.setKeyword(candidate.getKeyword());
            row.setWeight(MINED_WEIGHT);
            row.setSource(SuggestSource.MINED.getCode());
            rows.add(row);
        }
        // 不包事务：INSERT IGNORE 已经让重复行变成跳过，中途失败重跑一次不会多出东西
        return baseMapper.batchInsertIgnore(rows);
    }

    private LambdaQueryChainWrapper<SearchSuggest> enabled() {
        return lambdaQuery().eq(SearchSuggest::getStatus, SearchSuggest.STATUS_ENABLED);
    }

    private SearchSuggest requireExists(Long id) {
        SearchSuggest existing = id == null ? null : getById(id);
        if (existing == null) {
            throw new BizException(ResultCode.NOT_FOUND, "建议词不存在：" + id);
        }
        return existing;
    }

    private static SuggestSource sourceOrDefault(Integer source) {
        SuggestSource parsed = SuggestSource.of(source);
        return parsed == null ? SuggestSource.MANUAL : parsed;
    }

    private static String normalizeKeyword(String keyword) {
        String normalized = trimToNull(keyword);
        if (normalized == null) {
            throw new BizException(ResultCode.VALIDATE_FAILED, "keyword 不能为空");
        }
        return normalized;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static int clampLimit(int limit) {
        return (int) Math.min(Math.max(limit, 1), MAX_LIMIT);
    }

    private static long clampSize(long size) {
        return Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
    }
}
