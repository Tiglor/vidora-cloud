package org.tiglor.content.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.tiglor.common.core.ApiResult;
import org.tiglor.common.log.annotation.BusinessType;
import org.tiglor.common.log.annotation.OperLog;
import org.tiglor.content.dto.FeedConfigRequest;
import org.tiglor.content.entity.FeedConfig;
import org.tiglor.content.service.FeedConfigService;

import java.util.List;
import java.util.Map;

/**
 * 推荐流配置。读取只要登录，写入需要 {@code content:feed:manage}。
 * <p>
 * 两个读接口刻意分开：{@code /configs/{feedType}} 摊平成 Map 给推荐服务在算法里直接取值，
 * {@code /list} 保留 id 和 description 给管理端渲染表格。返回类型不同，不能合并。
 * </p>
 */
@RestController
@RequestMapping("/feed-configs")
@RequiredArgsConstructor
public class FeedConfigController {

    private final FeedConfigService service;

    /**
     * 查询推荐流配置项
     *
     * <p>{@code key → value} 的扁平视图，走缓存，给推荐服务在算法里直接取值。</p>
     */
    @GetMapping("/configs/{feedType}")
    public ApiResult<Map<String, String>> configs(@PathVariable String feedType) {
        return ApiResult.ok(service.configsOf(feedType));
    }

    /**
     * 查询推荐流配置列表
     *
     * <p>管理端用，带 id 与 description。</p>
     */
    @GetMapping("/list")
    @PreAuthorize("hasAuthority('content:feed:manage')")
    public ApiResult<List<FeedConfig>> list(@RequestParam String feedType) {
        return ApiResult.ok(service.listByFeedType(feedType));
    }

    /**
     * 新增或修改配置项
     *
     * <p>{@code (feedType, configKey)} 相同就是改值，不会多出一行。</p>
     */
    @PutMapping
    @PreAuthorize("hasAuthority('content:feed:manage')")
    @OperLog(title = "信息流配置", type = BusinessType.UPDATE)
    public ApiResult<FeedConfig> upsert(@Valid @RequestBody FeedConfigRequest request) {
        return ApiResult.ok(service.upsert(request));
    }

    /**
     * 删除配置项
     *
     * <p>物理删行，表上没有 {@code is_deleted}。</p>
     * <p>
     * 删掉之后这个 key 就不出现在 {@code /configs/{feedType}} 的 Map 里，
     * 对推荐服务等同于「没配」，它会回落到代码里的默认参数。想恢复就重新 upsert 同一个 key，
     * 那会是一行新的 id。
     * </p>
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('content:feed:manage')")
    @OperLog(title = "信息流配置", type = BusinessType.DELETE)
    public ApiResult<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResult.ok();
    }
}
