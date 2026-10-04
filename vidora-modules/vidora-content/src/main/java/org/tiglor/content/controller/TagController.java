package org.tiglor.content.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
import org.tiglor.content.dto.TagRequest;
import org.tiglor.content.entity.Tag;
import org.tiglor.content.service.TagService;

import java.util.List;

/**
 * 视频标签。热门标签和联想是公开的字典读取，管理操作需要 {@code content:tag:manage}。
 * <p>
 * {@code limit} 在进服务之前就被夹住：它同时是热门标签的缓存 key，
 * 不设上限的话外部随便传几个不同的整数就能往 Redis 里塞任意多条缓存。
 * </p>
 */
@RestController
@RequestMapping("/tags")
@RequiredArgsConstructor
public class TagController {

    private final TagService service;

    /** 标签云：启用状态、按使用次数倒序 */
    @GetMapping("/hot")
    public ApiResult<List<Tag>> hot(@RequestParam(defaultValue = "20") int limit) {
        return ApiResult.ok(service.hot(clampLimit(limit)));
    }

    /** 上传页的标签联想，按名字前缀匹配 */
    @GetMapping("/suggest")
    public ApiResult<List<Tag>> suggest(@RequestParam(required = false) String keyword,
                                        @RequestParam(defaultValue = "10") int limit) {
        return ApiResult.ok(service.suggest(keyword, clampLimit(limit)));
    }

    @GetMapping("/page")
    @PreAuthorize("hasAuthority('content:tag:manage')")
    public ApiResult<Page<Tag>> page(@RequestParam(defaultValue = "1") long current,
                                     @RequestParam(defaultValue = "20") long size,
                                     @RequestParam(required = false) String keyword,
                                     @RequestParam(required = false) Integer status) {
        return ApiResult.ok(service.page(current, size, keyword, status));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('content:tag:manage')")
    public ApiResult<Tag> create(@Valid @RequestBody TagRequest request) {
        return ApiResult.ok(service.create(request.getName()));
    }

    /** 禁用(0) / 启用(1) */
    @PutMapping("/{id}/status")
    @PreAuthorize("hasAuthority('content:tag:manage')")
    public ApiResult<Void> setStatus(@PathVariable Long id, @RequestParam int status) {
        service.setStatus(id, status);
        return ApiResult.ok();
    }

    /** 只有没人用的标签才允许删除，还在用的请改成禁用 */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('content:tag:manage')")
    public ApiResult<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResult.ok();
    }

    /** limit 是热门标签的缓存 key，必须先夹住再进服务，否则外部能往 Redis 里塞任意多个 key */
    private static int clampLimit(int limit) {
        return Math.min(Math.max(limit, 1), TagService.MAX_LIMIT);
    }
}
