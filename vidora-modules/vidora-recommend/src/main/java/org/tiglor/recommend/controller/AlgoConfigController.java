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
 * {@code scene} / {@code algoType} 在**这里**就转成枚举再传给服务：它们一起构成缓存 key，
 * 传字符串进去的话 {@code "HOME:CF"} 和 {@code "home:cf"} 会各占一个内容相同的条目。
 * 顺带把非法取值挡在缓存之前——不然一个拼错的场景名会在 Redis 里留下一条永远命中不了的记录。
 * </p>
 */
@RestController
@RequestMapping("/algo-configs")
@RequiredArgsConstructor
public class AlgoConfigController {

    private final AlgoConfigService service;

    /** key → value 的扁平视图，给算法侧直接取参数，走缓存 */
    @GetMapping("/configs")
    public ApiResult<Map<String, String>> configs(@RequestParam String scene,
                                                  @RequestParam String algoType) {
        return ApiResult.ok(service.configsOf(RecommendScene.of(scene), AlgoType.of(algoType)));
    }

    /** 某个场景下全部启用的配置项，带 id 与 description */
    @GetMapping("/list")
    public ApiResult<List<AlgoConfig>> listEnabled(@RequestParam String scene) {
        return ApiResult.ok(service.listEnabled(RecommendScene.of(scene)));
    }

    @GetMapping("/page")
    @PreAuthorize("hasAuthority('recommend:manage')")
    public ApiResult<Page<AlgoConfig>> page(@RequestParam(defaultValue = "1") long current,
                                            @RequestParam(defaultValue = "20") long size,
                                            @RequestParam(required = false) String scene,
                                            @RequestParam(required = false) String algoType,
                                            @RequestParam(required = false) Integer status) {
        return ApiResult.ok(service.page(current, size, scene, algoType, status));
    }

    /** (scene, algoType, configKey) 相同就是改值，不会多出一行；不会改动 status */
    @PutMapping
    @PreAuthorize("hasAuthority('recommend:manage')")
    public ApiResult<AlgoConfig> upsert(@Valid @RequestBody AlgoConfigRequest request) {
        return ApiResult.ok(service.upsert(request));
    }

    /** 禁用(0) / 启用(1)。禁用意味着算法回落到默认参数 */
    @PutMapping("/{id}/status")
    @PreAuthorize("hasAuthority('recommend:manage')")
    public ApiResult<Void> setStatus(@PathVariable Long id, @RequestParam int status) {
        service.setStatus(id, status);
        return ApiResult.ok();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('recommend:manage')")
    public ApiResult<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResult.ok();
    }
}
