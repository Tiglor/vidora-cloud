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
import org.tiglor.common.log.annotation.BusinessType;
import org.tiglor.common.log.annotation.OperLog;
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

    /**
     * 查询热门标签列表
     *
     * <p>标签云用：只给启用状态，按使用次数倒序。</p>
     */
    @GetMapping("/hot")
    public ApiResult<List<Tag>> hot(@RequestParam(defaultValue = "20") int limit) {
        return ApiResult.ok(service.hot(clampLimit(limit)));
    }

    /**
     * 查询标签联想列表
     *
     * <p>上传页输入框用，按名字前缀匹配。</p>
     */
    @GetMapping("/suggest")
    public ApiResult<List<Tag>> suggest(@RequestParam(required = false) String keyword,
                                        @RequestParam(defaultValue = "10") int limit) {
        return ApiResult.ok(service.suggest(keyword, clampLimit(limit)));
    }

    /**
     * 标签分页
     *
     * <p>管理端列表，按 {@code use_count} 倒序，最热的在前。</p>
     *
     * @param keyword 名字包含匹配（{@code LIKE '%kw%'}），和 {@code /suggest} 的前缀匹配不是一回事
     * @param status  0-禁用 / 1-启用；不传时两种都列，而 {@code /hot} 和 {@code /suggest} 只给启用的
     */
    @GetMapping("/page")
    @PreAuthorize("hasAuthority('content:tag:manage')")
    public ApiResult<Page<Tag>> page(@RequestParam(defaultValue = "1") long current,
                                     @RequestParam(defaultValue = "20") long size,
                                     @RequestParam(required = false) String keyword,
                                     @RequestParam(required = false) Integer status) {
        return ApiResult.ok(service.page(current, size, keyword, status));
    }

    /**
     * 新建标签
     *
     * <p>名字全表排重（大小写不同算同一个），初始使用次数 0、直接建成启用态。</p>
     */
    @PostMapping
    @PreAuthorize("hasAuthority('content:tag:manage')")
    @OperLog(title = "标签管理", type = BusinessType.INSERT)
    public ApiResult<Tag> create(@Valid @RequestBody TagRequest request) {
        return ApiResult.ok(service.create(request.getName()));
    }

    /**
     * 修改标签状态
     *
     * <p>0-禁用 / 1-启用。</p>
     */
    @PutMapping("/{id}/status")
    @PreAuthorize("hasAuthority('content:tag:manage')")
    @OperLog(title = "标签管理", type = BusinessType.CHANGE_STATUS)
    public ApiResult<Void> setStatus(@PathVariable Long id, @RequestParam int status) {
        service.setStatus(id, status);
        return ApiResult.ok();
    }

    /**
     * 删除标签
     *
     * <p>只有没人用的标签才允许删除，还在用的请改成禁用。</p>
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('content:tag:manage')")
    @OperLog(title = "标签管理", type = BusinessType.DELETE)
    public ApiResult<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResult.ok();
    }

    /** limit 是热门标签的缓存 key，必须先夹住再进服务，否则外部能往 Redis 里塞任意多个 key */
    private static int clampLimit(int limit) {
        return Math.min(Math.max(limit, 1), TagService.MAX_LIMIT);
    }
}
