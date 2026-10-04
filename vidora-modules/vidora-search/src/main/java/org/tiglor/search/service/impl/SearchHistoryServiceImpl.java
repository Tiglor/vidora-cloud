package org.tiglor.search.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tiglor.common.core.BizException;
import org.tiglor.common.core.ResultCode;
import org.tiglor.search.entity.SearchHistory;
import org.tiglor.search.mapper.SearchHistoryMapper;
import org.tiglor.search.mapper.SearchKeywordStatMapper;
import org.tiglor.search.service.SearchHistoryService;

import java.time.LocalDate;

/**
 * 用户搜索历史。
 * <p>
 * 原先的壳控制器五个方法直接转发 {@code IService}，全都是越权的：
 * {@code GET /search/page} 不带 {@code user_id} 过滤，任何登录用户都能翻遍全站的搜索历史；
 * {@code GET /search/{id}} 和 {@code DELETE /search/{id}} 同样不校验归属；
 * {@code POST /search} 收裸 {@code SearchHistory} 实体，调用方自己填 {@code userId}、
 * {@code searchCount} 就能伪造任何人的历史；{@code PUT /search/{id}} 能把任意行改成任意用户任意词。
 * 五条全部替换掉了。
 * </p>
 */
@Service
@RequiredArgsConstructor
public class SearchHistoryServiceImpl extends ServiceImpl<SearchHistoryMapper, SearchHistory>
        implements SearchHistoryService {

    private static final long MAX_PAGE_SIZE = 100L;

    private final SearchKeywordStatMapper statMapper;

    @Override
    @Transactional
    public void record(Long userId, String keyword, long resultCount) {
        Long uid = requireLogin(userId);
        String normalized = normalizeKeyword(keyword);
        // 统计不分用户，先写它：历史是「这个人的」，词频是「全站的」，
        // 两者放在一个事务里只是为了要么都记上要么都不记，避免出现只涨了一边的半天数据
        statMapper.upsertStat(normalized, LocalDate.now(), Math.max(resultCount, 0));
        baseMapper.upsertSearch(uid, normalized);
    }

    @Override
    public Page<SearchHistory> myHistory(Long userId, long current, long size) {
        Long uid = requireLogin(userId);
        // uk_user_keyword 的最左列是 user_id，等值过滤走得了它；
        // 但排序键 last_search_time 不在这个索引里，所以还有一次 filesort。
        // 一个人的历史行数是「搜过多少个不同的词」，量级很小，不值得为它再加一个 (user_id, last_search_time) 索引
        return lambdaQuery()
                .eq(SearchHistory::getUserId, uid)
                .orderByDesc(SearchHistory::getLastSearchTime)
                // id 是兜底排序键：last_search_time 是秒级精度，同一秒里的两条没有稳定顺序
                .orderByDesc(SearchHistory::getId)
                .page(new Page<>(Math.max(current, 1), clampSize(size)));
    }

    @Override
    public boolean removeMine(Long userId, Long id) {
        Long uid = requireLogin(userId);
        if (id == null) {
            throw new BizException(ResultCode.VALIDATE_FAILED, "id 不能为空");
        }
        // 带上 user_id：不带的话猜到一个自增 id 就能删掉别人的历史
        return baseMapper.delete(Wrappers.<SearchHistory>lambdaQuery()
                .eq(SearchHistory::getId, id)
                .eq(SearchHistory::getUserId, uid)) > 0;
    }

    @Override
    public int clearMine(Long userId) {
        Long uid = requireLogin(userId);
        return baseMapper.delete(Wrappers.<SearchHistory>lambdaQuery()
                .eq(SearchHistory::getUserId, uid));
    }

    /**
     * trim + 把连续空白压成一个空格。
     * <p>
     * 压空白不是为了好看：{@code uk_user_keyword} 和 {@code uk_keyword_date} 都是精确匹配，
     * {@code "科幻  电影"} 和 {@code "科幻 电影"} 会各占一行，同一个词的热度被劈成两半，
     * 词频统计和热词榜都会失真。
     * </p>
     * <p>
     * <b>不做大小写归一</b>：{@code utf8mb4_unicode_ci} 下 {@code uk_keyword} 本来就把
     * {@code Java} 和 {@code java} 当同一行，所以先写进去的那种拼法会被保留，后来的只加计数。
     * 全部转小写能保证一致，但联想框里出现 {@code iphone} 而不是 {@code iPhone} 是可见的质量问题，
     * 两害相权选了保留原样。
     * </p>
     */
    private static String normalizeKeyword(String keyword) {
        if (keyword == null) {
            throw new BizException(ResultCode.VALIDATE_FAILED, "keyword 不能为空");
        }
        String normalized = keyword.trim().replaceAll("\\s+", " ");
        if (normalized.isEmpty()) {
            throw new BizException(ResultCode.VALIDATE_FAILED, "keyword 不能为空");
        }
        return normalized;
    }

    private static Long requireLogin(Long userId) {
        if (userId == null) {
            throw new BizException(ResultCode.UNAUTHORIZED, "未登录");
        }
        return userId;
    }

    private static long clampSize(long size) {
        return Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
    }
}
