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
import org.tiglor.content.dto.CategoryNode;
import org.tiglor.content.dto.CategoryRequest;
import org.tiglor.content.entity.Category;
import org.tiglor.content.service.CategoryService;

import java.util.List;

/**
 * 视频分类。读取只要登录，增删改需要 {@code content:category:manage}。
 * <p>
 * 写入一律收 {@link CategoryRequest} 而不是裸实体：裸实体能让调用方自己填 id、
 * createTime 这些不该由外部决定的字段，一次「新增」就变成对任意行的覆写。
 * </p>
 */
@RestController
@RequestMapping("/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService service;

    /** 启用状态的分类（走缓存），用于首页/上传页的分类选择器 */
    @GetMapping("/list")
    public ApiResult<List<Category>> listEnabled() {
        return ApiResult.ok(service.listEnabled());
    }

    /** 同一批启用分类的树形结构（走缓存），用于侧边栏 */
    @GetMapping("/tree")
    public ApiResult<List<CategoryNode>> tree() {
        return ApiResult.ok(service.tree());
    }

    @GetMapping("/page")
    @PreAuthorize("hasAuthority('content:category:manage')")
    public ApiResult<Page<Category>> page(@RequestParam(defaultValue = "1") long current,
                                          @RequestParam(defaultValue = "20") long size,
                                          @RequestParam(required = false) Long parentId,
                                          @RequestParam(required = false) Integer status) {
        return ApiResult.ok(service.page(current, size, parentId, status));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('content:category:manage')")
    @OperLog(title = "分类管理", type = BusinessType.INSERT)
    public ApiResult<Category> create(@Valid @RequestBody CategoryRequest request) {
        return ApiResult.ok(service.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('content:category:manage')")
    @OperLog(title = "分类管理", type = BusinessType.UPDATE)
    public ApiResult<Category> update(@PathVariable Long id, @Valid @RequestBody CategoryRequest request) {
        return ApiResult.ok(service.update(id, request));
    }

    /** 禁用(0) / 启用(1)。下架一个分类应该走这里，删除只在它确实没被用时才允许 */
    @PutMapping("/{id}/status")
    @PreAuthorize("hasAuthority('content:category:manage')")
    @OperLog(title = "分类管理", type = BusinessType.CHANGE_STATUS)
    public ApiResult<Void> setStatus(@PathVariable Long id, @RequestParam int status) {
        service.setStatus(id, status);
        return ApiResult.ok();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('content:category:manage')")
    @OperLog(title = "分类管理", type = BusinessType.DELETE)
    public ApiResult<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResult.ok();
    }
}
