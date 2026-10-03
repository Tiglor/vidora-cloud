package com.video.platform.userservice.vo;

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
    private List<MenuVO> children = new ArrayList<>();
}
