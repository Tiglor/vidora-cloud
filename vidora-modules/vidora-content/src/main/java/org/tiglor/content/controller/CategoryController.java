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

    /**
     * 查询启用分类列表
     *
     * <p>首页／上传页的分类选择器用，走缓存。</p>
     */
    @GetMapping("/list")
    public ApiResult<List<Category>> listEnabled() {
        return ApiResult.ok(service.listEnabled());
    }

    /**
     * 查询分类树
     *
     * <p>侧边栏用，和 {@code /list} 给的是同一批启用分类，只是拼成树，走缓存。</p>
     */
    @GetMapping("/tree")
    public ApiResult<List<CategoryNode>> tree() {
        return ApiResult.ok(service.tree());
    }

    /**
     * 分类分页
     *
     * <p>管理端列表，可按父级和启用状态筛，按 {@code sort_order} 升序排。</p>
     *
     * @param parentId 只列这一层的直接子分类；不传则不分层，传 0 表示只要顶级分类
     * @param status   0-禁用 / 1-启用；不传时两种都列，而 {@code /list} 和 {@code /tree} 只给启用的
     */
    @GetMapping("/page")
    @PreAuthorize("hasAuthority('content:category:manage')")
    public ApiResult<Page<Category>> page(@RequestParam(defaultValue = "1") long current,
                                          @RequestParam(defaultValue = "20") long size,
                                          @RequestParam(required = false) Long parentId,
                                          @RequestParam(required = false) Integer status) {
        return ApiResult.ok(service.page(current, size, parentId, status));
    }

    /**
     * 新建分类
     *
     * <p>父级必须已存在，同一父级下不能重名。</p>
     * <p>
     * 重名判定走列的 {@code _ci} 排序规则，大小写不同算同一个名字。
     * {@code sortOrder} 不传按 0，{@code status} 不传建成启用——新建即对外可见，下架是之后显式的动作。
     * </p>
     */
    @PostMapping
    @PreAuthorize("hasAuthority('content:category:manage')")
    @OperLog(title = "分类管理", type = BusinessType.INSERT)
    public ApiResult<Category> create(@Valid @RequestBody CategoryRequest request) {
        return ApiResult.ok(service.create(request));
    }

    /**
     * 修改分类
     *
     * <p>换父级时会沿新父级往上逐层查，防止把这一支挪到自己的子孙下面而成环。</p>
     * <p>
     * {@code sortOrder}、{@code status} 没传就保持原值——只想改名时不必先把这两个字段读出来再回填。
     * 成环的分类在 {@code /tree} 里整支挂不上根、会直接消失且界面改不回来，所以这一步不能省。
     * </p>
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('content:category:manage')")
    @OperLog(title = "分类管理", type = BusinessType.UPDATE)
    public ApiResult<Category> update(@PathVariable Long id, @Valid @RequestBody CategoryRequest request) {
        return ApiResult.ok(service.update(id, request));
    }

    /**
     * 修改分类状态
     *
     * <p>0-禁用 / 1-启用。下架一个分类应该走这里，删除只在它确实没被用时才允许。</p>
     */
    @PutMapping("/{id}/status")
    @PreAuthorize("hasAuthority('content:category:manage')")
    @OperLog(title = "分类管理", type = BusinessType.CHANGE_STATUS)
    public ApiResult<Void> setStatus(@PathVariable Long id, @RequestParam int status) {
        service.setStatus(id, status);
        return ApiResult.ok();
    }

    /**
     * 删除分类
     *
     * <p>物理删除（表上没有 {@code is_deleted}），有子分类时会被拒。</p>
     * <p>
     * 下架请走 {@code PUT /{id}/status}：删除只在它确实没被下级用到时才允许。
     * 注意这里不检查还有多少视频挂在这个分类上，那是 video-service 的事。
     * </p>
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('content:category:manage')")
    @OperLog(title = "分类管理", type = BusinessType.DELETE)
    public ApiResult<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResult.ok();
    }
}
