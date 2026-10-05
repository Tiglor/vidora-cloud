package org.tiglor.content.dto;

import lombok.Data;
import org.tiglor.content.entity.Category;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 分类树节点。
 * <p>
 * 前端要的是嵌套结构，而表里存的是 {@code parent_id} 平铺。组装在内存里做一次：
 * 整张启用分类通常就几十行，一次查询 + 一次分组，比按层递归查库（每层一次往返）便宜得多。
 * </p>
 */
@Data
public class CategoryNode {

    private Long id;
    private Long parentId;
    private String name;
    private String iconUrl;
    private Integer sortOrder;
    private Integer status;
    private LocalDateTime createTime;
    private List<CategoryNode> children = new ArrayList<>();

    public static CategoryNode of(Category category) {
        CategoryNode node = new CategoryNode();
        node.setId(category.getId());
        node.setParentId(category.getParentId());
        node.setName(category.getName());
        node.setIconUrl(category.getIconUrl());
        node.setSortOrder(category.getSortOrder());
        node.setStatus(category.getStatus());
        node.setCreateTime(category.getCreateTime());
        return node;
    }
}
