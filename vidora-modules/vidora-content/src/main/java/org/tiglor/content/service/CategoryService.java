package org.tiglor.content.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.IService;
import org.tiglor.content.dto.CategoryNode;
import org.tiglor.content.dto.CategoryRequest;
import org.tiglor.content.entity.Category;

import java.util.List;

public interface CategoryService extends IService<Category> {

    /** 启用状态的分类，按 sort_order 升序；结果走 Redis 缓存 */
    List<Category> listEnabled();

    /** 启用分类组装成的树，一次查询在内存里拼，不按层递归查库 */
    List<CategoryNode> tree();

    /** 管理端分页，parentId / status 都可选 */
    Page<Category> page(long current, long size, Long parentId, Integer status);

    Category create(CategoryRequest request);

    Category update(Long id, CategoryRequest request);

    /** 物理删除；有子分类时拒绝 */
    void delete(Long id);

    /** 启用 / 禁用，重复设成同一个值是幂等的 */
    void setStatus(Long id, int status);
}
