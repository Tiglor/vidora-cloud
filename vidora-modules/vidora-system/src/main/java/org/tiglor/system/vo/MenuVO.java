package org.tiglor.system.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** 菜单树节点 */
@Data
public class MenuVO {

    private Long id;
    private Long parentId;
    private String menuName;
    private Integer menuType;
    private String path;
    private String component;
    private String icon;
    private Integer sortOrder;
    private String permissionCode;
    private Integer visible;
    /** 0-禁用 1-正常。写入接口收的是裸实体所以这个值一直能被改，但 listMenusForUser 按它过滤，读不出来就没法核对 */
    private Integer status;
    private List<MenuVO> children = new ArrayList<>();
}
