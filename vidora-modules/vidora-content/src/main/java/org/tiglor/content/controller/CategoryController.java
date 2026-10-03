package org.tiglor.content.controller;

import org.tiglor.content.entity.Category;
import org.tiglor.content.service.CategoryService;
import org.tiglor.common.core.ApiResult;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService service;

    @GetMapping("/page")
    public ApiResult<Page<Category>> page(@RequestParam(defaultValue = "1") long current,
                                           @RequestParam(defaultValue = "10") long size) {
        return ApiResult.ok(service.page(new Page<>(current, size)));
    }

    @GetMapping("/{id}")
    public ApiResult<Category> getById(@PathVariable Long id) {
        return ApiResult.ok(service.getById(id));
    }

    @PostMapping
    public ApiResult<Boolean> save(@RequestBody Category entity) {
        return ApiResult.ok(service.save(entity));
    }

    @PutMapping("/{id}")
    public ApiResult<Boolean> update(@PathVariable Long id, @RequestBody Category entity) {
        entity.setId(id);
        return ApiResult.ok(service.updateById(entity));
    }

    @DeleteMapping("/{id}")
    public ApiResult<Boolean> remove(@PathVariable Long id) {
        return ApiResult.ok(service.removeById(id));
    }
}
