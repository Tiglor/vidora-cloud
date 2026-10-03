package org.tiglor.recommend.controller;

import org.tiglor.recommend.entity.RecommendResult;
import org.tiglor.recommend.service.RecommendResultService;
import org.tiglor.common.core.ApiResult;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/recommends")
@RequiredArgsConstructor
public class RecommendResultController {

    private final RecommendResultService service;

    @GetMapping("/page")
    public ApiResult<Page<RecommendResult>> page(@RequestParam(defaultValue = "1") long current,
                                           @RequestParam(defaultValue = "10") long size) {
        return ApiResult.ok(service.page(new Page<>(current, size)));
    }

    @GetMapping("/{id}")
    public ApiResult<RecommendResult> getById(@PathVariable Long id) {
        return ApiResult.ok(service.getById(id));
    }

    @PostMapping
    public ApiResult<Boolean> save(@RequestBody RecommendResult entity) {
        return ApiResult.ok(service.save(entity));
    }

    @PutMapping("/{id}")
    public ApiResult<Boolean> update(@PathVariable Long id, @RequestBody RecommendResult entity) {
        entity.setId(id);
        return ApiResult.ok(service.updateById(entity));
    }

    @DeleteMapping("/{id}")
    public ApiResult<Boolean> remove(@PathVariable Long id) {
        return ApiResult.ok(service.removeById(id));
    }
}
