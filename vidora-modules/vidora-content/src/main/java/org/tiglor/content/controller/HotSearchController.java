package org.tiglor.content.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.tiglor.common.core.ApiResult;
import org.tiglor.content.dto.HotSearchRequest;
import org.tiglor.content.entity.HotSearch;
import org.tiglor.content.service.HotSearchService;

import java.time.LocalDate;
import java.util.List;

/**
 * 热搜榜。榜单本身是公开读的，加词/重排/下线需要 {@code content:hotsearch:manage}。
 * <p>
 * 日期默认值在这里补，不在服务层补：{@code date} 同时是缓存 key，
 * Spring Cache 拿到 null key 会直接抛异常，缓存一次都不会命中。
 * </p>
 */
@RestController
@RequestMapping("/hot-searches")
@RequiredArgsConstructor
public class HotSearchController {

    private final HotSearchService service;

    /** 某天上线中的词，按 rank 升序；不传日期就是今天 */
    @GetMapping
    public ApiResult<List<HotSearch>> board(@RequestParam(required = false)
                                            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ApiResult.ok(service.board(date == null ? LocalDate.now() : date));
    }

    /** 加词或刷新热度。rank 由服务端算，请求体里传了也不认 */
    @PostMapping
    @PreAuthorize("hasAuthority('content:hotsearch:manage')")
    public ApiResult<HotSearch> upsert(@Valid @RequestBody HotSearchRequest request) {
        return ApiResult.ok(service.upsert(request));
    }

    /** 按当前热度重排某天榜单，返回真正被改动的行数 */
    @PostMapping("/rebuild")
    @PreAuthorize("hasAuthority('content:hotsearch:manage')")
    public ApiResult<Integer> rebuild(@RequestParam(required = false)
                                      @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ApiResult.ok(service.rebuild(date == null ? LocalDate.now() : date));
    }

    /** 上线(1) / 下线(0)，下线敏感词走这里 */
    @PutMapping("/{id}/status")
    @PreAuthorize("hasAuthority('content:hotsearch:manage')")
    public ApiResult<Void> setStatus(@PathVariable Long id, @RequestParam int status) {
        service.setStatus(id, status);
        return ApiResult.ok();
    }
}
