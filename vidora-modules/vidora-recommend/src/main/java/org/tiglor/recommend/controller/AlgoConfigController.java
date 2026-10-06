package org.tiglor.recommend.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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
import org.tiglor.recommend.dto.AlgoConfigRequest;
import org.tiglor.recommend.entity.AlgoConfig;
import org.tiglor.recommend.enums.AlgoType;
import org.tiglor.recommend.enums.RecommendScene;
import org.tiglor.recommend.service.AlgoConfigService;

import java.util.List;
import java.util.Map;

/**
 * 推荐算法配置。读取只要登录，写入需要 {@code recommend:manage}。
 * <p>
 * {@code scene} / {@code algoType} 在「这里」就转成枚举再传给服务：它们一起构成缓存 key，
 * 传字符串进去的话 {@code "HOME:CF"} 和 {@code "home:cf"} 会各占一个内容相同的条目。
 * 顺带把非法取值挡在缓存之前——不然一个拼错的场景名会在 Redis 里留下一条永远命中不了的记录。
 * </p>
 */
@RestController
@RequestMapping("/algo-configs")
@RequiredArgsConstructor
public class AlgoConfigController {

    private final AlgoConfigService service;

    /**
     * 查询算法配置参数
     *
     * <p>摊成 key → value 的扁平视图，给算法侧直接取参数，走缓存。</p>
     */
    @GetMapping("/configs")
    public ApiResult<Map<String, String>> configs(@RequestParam String scene,
                                                  @RequestParam String algoType) {
        return ApiResult.ok(service.configsOf(RecommendScene.of(scene), AlgoType.of(algoType)));
    }

    /**
     * 查询启用算法配置列表
     *
     * <p>某个场景下全部启用的配置项，带 id 与 description。</p>
     */
    @GetMapping("/list")
    public ApiResult<List<AlgoConfig>> listEnabled(@RequestParam String scene) {
        return ApiResult.ok(service.listEnabled(RecommendScene.of(scene)));
    }

    /**
     * 算法配置分页
     *
     * <p>
     * 按场景、算法、key 依次升序，同一组参数在表格里挨在一起。这里查的是整张表，
     * 不像 {@code /configs} 和 {@code /list} 那样只给启用项——被禁用的配置也得看得见，
     * 否则运营没法把它改回来。
     * </p>
     *
     * @param scene    home / follow / topic，非法取值当场报错而不是查出一页空结果
     * @param algoType cf / deep / heatmap，同样先验取值
     * @param status   0-禁用 / 1-启用；不传时两种都列
     */
    @GetMapping("/page")
    @PreAuthorize("hasAuthority('recommend:manage')")
    public ApiResult<Page<AlgoConfig>> page(@RequestParam(defaultValue = "1") long current,
                                            @RequestParam(defaultValue = "20") long size,
                                            @RequestParam(required = false) String scene,
                                            @RequestParam(required = false) String algoType,
                                            @RequestParam(required = false) Integer status) {
        return ApiResult.ok(service.page(current, size, scene, algoType, status));
    }

    /**
     * 新建或修改算法配置
     *
     * <p>{@code (scene, algoType, configKey)} 相同就是改值，不会多出一行；不会改动 {@code status}。</p>
     */
    @PutMapping
    @PreAuthorize("hasAuthority('recommend:manage')")
    @OperLog(title = "算法配置", type = BusinessType.UPDATE)
    public ApiResult<AlgoConfig> upsert(@Valid @RequestBody AlgoConfigRequest request) {
        return ApiResult.ok(service.upsert(request));
    }

    /**
     * 修改算法配置状态
     *
     * <p>禁用(0) / 启用(1)。禁用意味着算法回落到默认参数。</p>
     */
    @PutMapping("/{id}/status")
    @PreAuthorize("hasAuthority('recommend:manage')")
    @OperLog(title = "算法配置", type = BusinessType.CHANGE_STATUS)
    public ApiResult<Void> setStatus(@PathVariable Long id, @RequestParam int status) {
        service.setStatus(id, status);
        return ApiResult.ok();
    }

    /**
     * 删除算法配置
     *
     * <p>
     * 物理删行，表上没有 {@code is_deleted}；删掉后这个 key 不再出现在 {@code /configs} 的 Map 里，
     * 算法回落到代码里的默认参数；
     * 想恢复就重新 upsert 同一个 (scene, algoType, configKey)，那会是一行新的 id。
     * </p>
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('recommend:manage')")
    @OperLog(title = "算法配置", type = BusinessType.DELETE)
    public ApiResult<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResult.ok();
    }
}
