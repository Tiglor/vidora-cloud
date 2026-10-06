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

    /** 本节点对应的分类 id */
    private Long id;
    /** 父分类 id，0 表示这是一级分类 */
    private Long parentId;
    /** 分类名 */
    private String name;
    /** 图标地址，没配过就是 null */
    private String iconUrl;
    /** 同一层内的展示顺序，越小越靠前 */
    private Integer sortOrder;
    /** 树只由那一份启用分类组装，所以这里恒为 1-启用 */
    private Integer status;
    /** 分类的创建时间，原样取自实体 */
    private LocalDateTime createTime;
    /**
     * 直接子节点，顺序与 {@code sortOrder} 一致；没有下级时是空数组而不是 null。
     * 父级已被禁用或删掉的节点不会挂在这一份里，而是被提到根上——藏起来的话
     * 运营就没有入口把它改回来了。
     */
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
