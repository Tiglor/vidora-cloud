package org.tiglor.search.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.IService;
import org.tiglor.search.entity.SearchKeywordStat;

import java.time.LocalDate;
import java.util.List;

/**
 * 搜索词统计。只读——写入方是 {@link SearchHistoryService#record}，
 * 这里不提供任何「改计数」的入口，否则统计数字就失去了意义。
 * <p>
 * 这张表和 content-service 的 {@code content_hot_search}（热搜榜）不是一回事：
 * 这里是**原始词频**，按天自动累积；那边是运营**发布**出去的榜单，
 * 带 {@code rank}、带上下架状态、可以人工插一条根本没人搜过的词。
 * 榜单应当从这份统计里取数，但两个服务分库，目前还没有这条链路。
 * </p>
 */
public interface SearchKeywordStatService extends IService<SearchKeywordStat> {

    /** {@link #hot} 的条数上限 */
    int MAX_HOT_LIMIT = 50;

    /**
     * 某一天的热词，按搜索次数倒序，命中 {@code idx_stat_date_count(stat_date, search_count)}。
     *
     * @param date null 表示今天
     */
    List<SearchKeywordStat> hot(LocalDate date, int limit);

    /** 管理端分页。keyword 是模糊匹配，date 不传就不按天过滤 */
    Page<SearchKeywordStat> page(long current, long size, LocalDate date, String keyword);
}
