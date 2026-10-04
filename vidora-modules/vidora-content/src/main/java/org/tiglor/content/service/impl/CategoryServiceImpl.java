package org.tiglor.content.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.tiglor.common.core.BizException;
import org.tiglor.common.core.ResultCode;
import org.tiglor.common.redis.CacheNames;
import org.tiglor.content.dto.CategoryNode;
import org.tiglor.content.dto.CategoryRequest;
import org.tiglor.content.entity.Category;
import org.tiglor.content.mapper.CategoryMapper;
import org.tiglor.content.service.CategoryService;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 视频分类。
 * <p>
 * 分类列表是首页/上传页每次都要拉、但几乎不变的字典数据，走 Redis 缓存；
 * 缓存的是整张启用列表（单 key），因此任何增删改都只能整体失效。
 * 树也是同一个缓存名下的另一个 key（{@code tree}），跟着一起失效。
 * </p>
 * <p>
 * 这个类真正要守的是 {@code parent_id} 的完整性：父级必须存在、不能自指、不能成环、
 * 同一父级下不能重名。表上没有任何约束能保证这些，全靠这里。
 * </p>
 */
@Service
public class CategoryServiceImpl extends ServiceImpl<CategoryMapper, Category> implements CategoryService {

    private static final long MAX_PAGE_SIZE = 100L;

    /** 分类层级的实际上限，同时也是 {@link #assertMovable} 里防死循环的边界 */
    private static final int MAX_TREE_DEPTH = 20;

    private static final int STATUS_ENABLED = 1;

    @Override
    // 命中 content_category 的 idx_status_sort(status, sort_order) 索引
    @Cacheable(cacheNames = CacheNames.CATEGORY_LIST, key = "'enabled'")
    public List<Category> listEnabled() {
        return lambdaQuery()
                .eq(Category::getStatus, STATUS_ENABLED)
                // id 是兜底排序键：sort_order 相同的分类不加它，翻页时顺序会抖
                .orderByAsc(Category::getSortOrder)
                .orderByAsc(Category::getId)
                .list();
    }

    @Override
    @Cacheable(cacheNames = CacheNames.CATEGORY_LIST, key = "'tree'")
    public List<CategoryNode> tree() {
        // 这里没有调 listEnabled()：同一个 bean 内部的自调用不走代理，@Cacheable 不会生效，
        // 与其留一个看起来在缓存其实没有的调用，不如把查询直接写出来，由 tree() 自己缓存
        List<Category> all = lambdaQuery()
                .eq(Category::getStatus, STATUS_ENABLED)
                .orderByAsc(Category::getSortOrder)
                .orderByAsc(Category::getId)
                .list();

        Map<Long, CategoryNode> byId = new LinkedHashMap<>();
        all.forEach(category -> byId.put(category.getId(), CategoryNode.of(category)));

        List<CategoryNode> roots = new ArrayList<>();
        for (CategoryNode node : byId.values()) {
            CategoryNode parent = node.getParentId() == null ? null : byId.get(node.getParentId());
            // 父级被禁用或已删除时把这一支提到根上，而不是让它凭空消失：
            // 前端看不见的分类就没人能把它改回来
            if (parent == null || parent == node) {
                roots.add(node);
            } else {
                parent.getChildren().add(node);
            }
        }
        return roots;
    }

    @Override
    public Page<Category> page(long current, long size, Long parentId, Integer status) {
        return lambdaQuery()
                .eq(parentId != null, Category::getParentId, parentId)
                .eq(status != null, Category::getStatus, status)
                .orderByAsc(Category::getSortOrder)
                .orderByAsc(Category::getId)
                .page(new Page<>(Math.max(current, 1), clampSize(size)));
    }

    // ---------- 写入：整张列表失效 ----------

    @Override
    @CacheEvict(cacheNames = CacheNames.CATEGORY_LIST, allEntries = true)
    public Category create(CategoryRequest request) {
        long parentId = normalizeParentId(request.getParentId());
        String name = normalizeName(request.getName());
        if (parentId != Category.ROOT_PARENT_ID) {
            requireExists(parentId, "父分类不存在：" + parentId);
        }
        requireNameAvailable(parentId, name, null);

        Category entity = new Category();
        entity.setParentId(parentId);
        entity.setName(name);
        entity.setIconUrl(trimToNull(request.getIconUrl()));
        entity.setSortOrder(request.getSortOrder() == null ? 0 : request.getSortOrder());
        entity.setStatus(request.getStatus() == null ? STATUS_ENABLED : request.getStatus());
        save(entity);
        return entity;
    }

    @Override
    @CacheEvict(cacheNames = CacheNames.CATEGORY_LIST, allEntries = true)
    public Category update(Long id, CategoryRequest request) {
        requireExists(id, "分类不存在：" + id);
        long parentId = normalizeParentId(request.getParentId());
        String name = normalizeName(request.getName());
        if (parentId != Category.ROOT_PARENT_ID) {
            requireExists(parentId, "父分类不存在：" + parentId);
        }
        assertMovable(id, parentId);
        requireNameAvailable(parentId, name, id);

        Category entity = new Category();
        entity.setId(id);
        entity.setParentId(parentId);
        entity.setName(name);
        entity.setIconUrl(trimToNull(request.getIconUrl()));
        // 排序与状态没传就保持原样：updateById 跳过 null 字段，这里不用自己回填 existing 的值
        entity.setSortOrder(request.getSortOrder());
        entity.setStatus(request.getStatus());
        updateById(entity);
        return getById(id);
    }

    @Override
    @CacheEvict(cacheNames = CacheNames.CATEGORY_LIST, allEntries = true)
    public void delete(Long id) {
        requireExists(id, "分类不存在：" + id);
        long children = lambdaQuery().eq(Category::getParentId, id).count();
        if (children > 0) {
            throw new BizException(ResultCode.VALIDATE_FAILED,
                    "该分类下还有 " + children + " 个子分类，请先删除或移走它们");
        }
        removeById(id);
    }

    @Override
    @CacheEvict(cacheNames = CacheNames.CATEGORY_LIST, allEntries = true)
    public void setStatus(Long id, int status) {
        requireStatus(status);
        requireExists(id, "分类不存在：" + id);
        // ne(status) 让「已经是目标值」的情况影响 0 行，据此可以区分「真的改了」和「本来就是这样」
        lambdaUpdate()
                .set(Category::getStatus, status)
                .eq(Category::getId, id)
                .ne(Category::getStatus, status)
                .update();
    }

    @Override
    @CacheEvict(cacheNames = CacheNames.CATEGORY_LIST, allEntries = true)
    public boolean save(Category entity) {
        return super.save(entity);
    }

    @Override
    @CacheEvict(cacheNames = CacheNames.CATEGORY_LIST, allEntries = true)
    public boolean updateById(Category entity) {
        return super.updateById(entity);
    }

    @Override
    @CacheEvict(cacheNames = CacheNames.CATEGORY_LIST, allEntries = true)
    public boolean removeById(Serializable id) {
        return super.removeById(id);
    }

    // ---------- 校验 ----------

    /**
     * 从新父级一路往上走，走到自己就说明这一挪会成环。
     * <p>
     * 环一旦进了库，{@link #tree} 组装时那一支会挂不上任何根节点从而整个消失，
     * 而且再也无法通过界面改回来——只能直接改表。所以这一步不能省。
     * </p>
     */
    private void assertMovable(Long id, long newParentId) {
        if (newParentId == id) {
            throw new BizException(ResultCode.VALIDATE_FAILED, "不能把自己设为父分类");
        }
        Long cursor = newParentId;
        for (int depth = 0; cursor != null && cursor != Category.ROOT_PARENT_ID; depth++) {
            if (depth >= MAX_TREE_DEPTH) {
                throw new BizException(ResultCode.FAIL,
                        "分类层级超过 " + MAX_TREE_DEPTH + " 层或已经成环，请先修正数据");
            }
            Category parent = getById(cursor);
            if (parent == null) {
                // 父链在这里断了，交由 requireExists 报「父分类不存在」
                return;
            }
            if (parent.getId().equals(id)) {
                throw new BizException(ResultCode.VALIDATE_FAILED, "不能把分类移到自己的子孙下面");
            }
            cursor = parent.getParentId();
        }
    }

    /**
     * 同一父级下不允许重名。
     * <p>
     * 等值比较走的是列的 {@code utf8mb4_unicode_ci} 排序规则，天然大小写不敏感，
     * 和 DDL 上新加的 {@code uk_parent_name} 语义一致。库里没有那个唯一键的话，
     * 两个管理员同时建同名分类就会都成功，选择器里出现两个一模一样的项。
     * </p>
     */
    private void requireNameAvailable(long parentId, String name, Long excludeId) {
        long duplicates = lambdaQuery()
                .eq(Category::getParentId, parentId)
                .eq(Category::getName, name)
                .ne(excludeId != null, Category::getId, excludeId)
                .count();
        if (duplicates > 0) {
            throw new BizException(ResultCode.VALIDATE_FAILED, "同一父分类下已经有叫「" + name + "」的分类");
        }
    }

    private Category requireExists(Long id, String message) {
        Category category = getById(id);
        if (category == null) {
            throw new BizException(ResultCode.NOT_FOUND, message);
        }
        return category;
    }

    private static long normalizeParentId(Long parentId) {
        return parentId == null ? Category.ROOT_PARENT_ID : parentId;
    }

    private static String normalizeName(String name) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty()) {
            throw new BizException(ResultCode.VALIDATE_FAILED, "分类名不能为空");
        }
        return trimmed;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static void requireStatus(int status) {
        if (status != 0 && status != 1) {
            throw new BizException(ResultCode.VALIDATE_FAILED, "status 只能是 0-禁用 或 1-启用");
        }
    }

    private static long clampSize(long size) {
        return Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
    }
}
