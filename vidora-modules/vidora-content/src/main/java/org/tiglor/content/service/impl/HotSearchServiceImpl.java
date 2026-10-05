package org.tiglor.content.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.tiglor.common.core.BizException;
import org.tiglor.common.core.ResultCode;
import org.tiglor.common.redis.CacheNames;
import org.tiglor.content.dto.HotSearchRequest;
import org.tiglor.content.entity.HotSearch;
import org.tiglor.content.mapper.HotSearchMapper;
import org.tiglor.content.service.HotSearchService;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 热搜榜单。
 * <p>
 * 榜单是「按天生成的快照」：一天的词汇集在 {@code rank_date} 下，{@code rank} 是这批词按热度
 * 排出来的相对位置。所以排名不接受外部指定，只能由 {@link #rebuild} 整批重算——
 * 让单个词的写入去改 rank，会把别的词挤到重复的名次上。
 * </p>
 */
@Service
public class HotSearchServiceImpl extends ServiceImpl<HotSearchMapper, HotSearch> implements HotSearchService {

    private static final int STATUS_ONLINE = 1;

    @Override
    // 命中 idx_rank_date(rank_date, rank)
    @Cacheable(cacheNames = CacheNames.HOT_SEARCH_BOARD, key = "#date")
    public List<HotSearch> board(LocalDate date) {
        requireDate(date);
        return lambdaQuery()
                .eq(HotSearch::getRankDate, date)
                .eq(HotSearch::getStatus, STATUS_ONLINE)
                .orderByAsc(HotSearch::getRank)
                // 还没重算过的新词 rank 都是 0，靠热度兜一下，不至于挤在榜首顺序随机
                .orderByDesc(HotSearch::getHeatScore)
                .list();
    }

    @Override
    // 刻意不缓存：管理端要的是「刚点完就能看到」，运营下线一个词之后刷新还看到它上线着，
    // 只会让人以为按钮没生效而反复点
    public List<HotSearch> adminBoard(LocalDate date, Integer status) {
        requireDate(date);
        return lambdaQuery()
                .eq(HotSearch::getRankDate, date)
                .eq(status != null, HotSearch::getStatus, status)
                .orderByAsc(HotSearch::getRank)
                .orderByDesc(HotSearch::getHeatScore)
                .list();
    }

    @Override
    @CacheEvict(cacheNames = CacheNames.HOT_SEARCH_BOARD, allEntries = true)
    public int rebuild(LocalDate date) {
        requireDate(date);
        List<HotSearch> rows = lambdaQuery()
                .eq(HotSearch::getRankDate, date)
                .eq(HotSearch::getStatus, STATUS_ONLINE)
                .orderByDesc(HotSearch::getHeatScore)
                .orderByDesc(HotSearch::getSearchCount)
                // id 是必需的第三排序键：热度相同的词没有稳定的兜底顺序，
                // 每次重算都会得到一份不同的名次，榜单在两次刷新之间反复横跳
                .orderByAsc(HotSearch::getId)
                .list();

        List<HotSearch> changed = new ArrayList<>();
        for (int index = 0; index < rows.size(); index++) {
            HotSearch row = rows.get(index);
            int rank = index + 1;
            if (row.getRank() == null || row.getRank() != rank) {
                // 只填 id 和 rank：updateById 跳过 null 字段，SET 子句里就只有 `rank` 一列
                HotSearch patch = new HotSearch();
                patch.setId(row.getId());
                patch.setRank(rank);
                changed.add(patch);
            }
        }
        if (!changed.isEmpty()) {
            updateBatchById(changed);
        }
        return changed.size();
    }

    @Override
    @CacheEvict(cacheNames = CacheNames.HOT_SEARCH_BOARD, allEntries = true)
    public HotSearch upsert(HotSearchRequest request) {
        String keyword = trimToNull(request.getKeyword());
        if (keyword == null) {
            throw new BizException(ResultCode.VALIDATE_FAILED, "keyword 不能为空");
        }
        LocalDate date = request.getRankDate() == null ? LocalDate.now() : request.getRankDate();
        int status = request.getStatus() == null ? STATUS_ONLINE : request.getStatus();
        requireStatus(status);

        baseMapper.upsert(keyword,
                request.getHeatScore() == null ? 0 : request.getHeatScore(),
                request.getSearchCount() == null ? 0L : request.getSearchCount(),
                status, date);
        // uk_keyword_date 保证至多一行；keyword 列是 _ci 排序规则，回读时大小写不敏感也能命中
        return lambdaQuery()
                .eq(HotSearch::getKeyword, keyword)
                .eq(HotSearch::getRankDate, date)
                .one();
    }

    @Override
    @CacheEvict(cacheNames = CacheNames.HOT_SEARCH_BOARD, allEntries = true)
    public void setStatus(Long id, int status) {
        requireStatus(status);
        if (getById(id) == null) {
            throw new BizException(ResultCode.NOT_FOUND, "榜单条目不存在：" + id);
        }
        lambdaUpdate()
                .set(HotSearch::getStatus, status)
                .eq(HotSearch::getId, id)
                .ne(HotSearch::getStatus, status)
                .update();
    }

    /**
     * 日期同时是缓存 key，Spring Cache 收到 null key 会直接抛异常而不是退化成不缓存，
     * 所以「不传日期就看今天」的兜底放在 controller 里做，到这里必须已经是具体日期。
     */
    private static void requireDate(LocalDate date) {
        if (date == null) {
            throw new BizException(ResultCode.VALIDATE_FAILED, "榜单日期不能为空");
        }
    }

    private static void requireStatus(int status) {
        if (status != 0 && status != 1) {
            throw new BizException(ResultCode.VALIDATE_FAILED, "status 只能是 0-下线 或 1-上线");
        }
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
