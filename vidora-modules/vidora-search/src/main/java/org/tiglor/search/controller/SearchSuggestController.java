package org.tiglor.search.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.tiglor.common.core.ApiResult;
import org.tiglor.search.dto.SuggestRequest;
import org.tiglor.search.entity.SearchSuggest;
import org.tiglor.search.service.SearchSuggestService;

import java.time.LocalDate;
import java.util.List;

/**
 * 搜索建议词。联想只要登录，管理操作要 {@code search:suggest:manage}。
 * <p>
 * 联想是整条搜索链路里请求量最大的接口——每敲一个字符就是一次——所以它整份进 Redis，
 * 但只有「未输入时的热门词」这一个视图进缓存：带前缀的查询 key 是用户输入的任意字符串，
 * 缓存它等于让外部决定 Redis 里有多少条记录。
 * </p>
 * <p>
 * {@code limit} 在进服务之前就被夹住：它同时是热门词的缓存 key，
 * 不设上限的话外部随便传几个不同的整数就能往 Redis 里塞任意多条缓存。
 * </p>
 */
@RestController
@RequestMapping("/search/suggests")
@RequiredArgsConstructor
public class SearchSuggestController {

    private final SearchSuggestService service;

    /**
     * 搜索框联想。前缀为空时返回按权重排的热门词（走缓存），
     * 有前缀时按前缀匹配（直接查库，走 {@code uk_keyword} 的范围扫描）。
     * <p>
     * 分支放在这里而不是服务层：热门词那条路径带 {@code @Cacheable}，
     * 同一个 bean 里的自调用不走 Spring 代理，塞在一个方法里缓存根本不会生效。
     * </p>
     */
    @GetMapping
    public ApiResult<List<SearchSuggest>> suggest(@RequestParam(required = false) String prefix,
                                                 @RequestParam(defaultValue = "10") int limit) {
        int max = clampLimit(limit);
        return ApiResult.ok(prefix == null || prefix.isBlank()
                ? service.top(max)
                : service.suggest(prefix, max));
    }

    @GetMapping("/page")
    @PreAuthorize("hasAuthority('search:suggest:manage')")
    public ApiResult<Page<SearchSuggest>> page(@RequestParam(defaultValue = "1") long current,
                                              @RequestParam(defaultValue = "20") long size,
                                              @RequestParam(required = false) String keyword,
                                              @RequestParam(required = false) Integer status,
                                              @RequestParam(required = false) Integer source) {
        return ApiResult.ok(service.page(current, size, keyword, status, source));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('search:suggest:manage')")
    public ApiResult<SearchSuggest> create(@Valid @RequestBody SuggestRequest request) {
        return ApiResult.ok(service.create(request));
    }

    /** 改词、权重、来源；不动 status */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('search:suggest:manage')")
    public ApiResult<SearchSuggest> update(@PathVariable Long id, @Valid @RequestBody SuggestRequest request) {
        return ApiResult.ok(service.update(id, request));
    }

    /** 禁用(0) / 启用(1)。禁用只是从联想框里摘掉，词还留着 */
    @PutMapping("/{id}/status")
    @PreAuthorize("hasAuthority('search:suggest:manage')")
    public ApiResult<Void> setStatus(@PathVariable Long id, @RequestParam int status) {
        service.setStatus(id, status);
        return ApiResult.ok();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('search:suggest:manage')")
    public ApiResult<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResult.ok();
    }

    /**
     * 从某一天的搜索统计里挖词，把还没收录的热词加成建议词（{@code source = 2}）。
     * <p>
     * 运营手工触发，不是定时任务：挖出来的词没人审过就会直接进联想框，
     * 得有人先看过门槛和候选再决定放不放。新词权重一律是 0，
     * 排在所有人工词之后，等运营逐个提权重。
     * </p>
     *
     * @param date     从哪一天的统计里挖，不传就是今天
     * @param minCount 搜索次数门槛，至少 1
     * @return 真正新增的条数，已存在的会被跳过
     */
    @PostMapping("/mine")
    @PreAuthorize("hasAuthority('search:suggest:manage')")
    public ApiResult<Integer> mine(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(defaultValue = "10") long minCount,
            @RequestParam(defaultValue = "50") int limit) {
        return ApiResult.ok(service.mine(date, minCount, limit));
    }

    /** limit 是热门建议词的缓存 key，必须先夹住再进服务，否则外部能往 Redis 里塞任意多个 key */
    private static int clampLimit(int limit) {
        return Math.min(Math.max(limit, 1), SearchSuggestService.MAX_LIMIT);
    }
}
