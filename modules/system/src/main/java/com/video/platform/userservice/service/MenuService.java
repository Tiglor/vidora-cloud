package com.video.platform.userservice.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.video.platform.userservice.entity.Menu;
import com.video.platform.userservice.vo.MenuVO;

import java.util.List;

public interface MenuService extends IService<Menu> {

    /**
     * 按用户ID查询其可见菜单树（用户 → 角色 → 菜单）
     * 只返回目录(1)与菜单(2)，按钮(3)不进菜单树，仅作为权限标识使用。
     */
    List<MenuVO> listMenusForUser(Long userId);

    /** 全部菜单树（管理端分配授权时用） */
    List<MenuVO> listAllTree();

    /** 某角色已授权的菜单ID */
    List<Long> listMenuIdsByRoleId(Long roleId);

    /** 给角色授权菜单（全量覆盖） */
    void assignMenusToRole(Long roleId, List<Long> menuIds);
}
