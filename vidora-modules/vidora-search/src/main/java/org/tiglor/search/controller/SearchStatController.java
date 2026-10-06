package org.tiglor.search.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.tiglor.common.core.ApiResult;
import org.tiglor.search.entity.SearchKeywordStat;
import org.tiglor.search.service.SearchKeywordStatService;

import java.time.LocalDate;
import java.util.List;

/**
 * 搜索词统计。只读，两个接口都要 {@code search:stat:view}。
 * <p>
 * 读也要权限，和标签、分类那些字典不一样：这张表是「全站用户在搜什么」的原始词频，
 * 里面有大量能反推出用户群体兴趣、甚至能看出某部片子正在被找但站内没有的数据。
 * 它给运营和分析看，不给普通用户看。
 * </p>
 * <p>
 * 日期默认值在服务层补（{@code null} 就是今天）。这里和热搜榜那边不同：
 * 榜单的 {@code rankDate} 同时是缓存 key，Spring Cache 拿到 null key 会直接抛异常，
 * 所以必须在进服务之前就补上；这张表不缓存，null 传进去没有副作用。
 * </p>
 */
@RestController
@RequestMapping("/search/stats")
@RequiredArgsConstructor
public class SearchStatController {

    private final SearchKeywordStatService service;

    /**
     * 查询某天热词
     *
     * <p>某一天的热词，按搜索次数倒序</p>
     */
    @GetMapping("/hot")
    @PreAuthorize("hasAuthority('search:stat:view')")
    public ApiResult<List<SearchKeywordStat>> hot(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(defaultValue = "20") int limit) {
        return ApiResult.ok(service.hot(date, limit));
    }

    /**
     * 搜索词统计分页
     *
     * <p>先按统计日期倒序，同一天内按搜索次数倒序</p>
     * <p>
     * {@code date} 这里不传就是「不按日期筛」，能一次翻到好几天的同一批词；
     * 而 {@code /hot} 不传时按今天算——两个接口的 null 语义不一样，别照着抄。
     * </p>
     *
     * @param date    按天精确匹配统计日期（{@code yyyy-MM-dd}）；不传则跨天查，见上
     * @param keyword 词本身做包含匹配（{@code LIKE '%kw%'}），不是前缀匹配
     */
    @GetMapping("/page")
    @PreAuthorize("hasAuthority('search:stat:view')")
    public ApiResult<Page<SearchKeywordStat>> page(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) String keyword) {
        return ApiResult.ok(service.page(current, size, date, keyword));
    }
}
