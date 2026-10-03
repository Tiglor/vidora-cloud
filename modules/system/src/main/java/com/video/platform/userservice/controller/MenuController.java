package com.video.platform.userservice.controller;

import com.video.platform.common.ApiResult;
import com.video.platform.common.security.UserContext;
import com.video.platform.userservice.entity.Menu;
import com.video.platform.userservice.service.MenuService;
import com.video.platform.userservice.vo.MenuVO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 菜单控制：
 * - GET /menus          当前登录用户的菜单树（前端据此渲染菜单）
 * - GET /menus/tree     全部菜单树（管理端）
 * - POST /menus/role/{roleId}  给角色授权菜单（核心：菜单控制）
 */
@RestController
@RequestMapping("/menus")
@RequiredArgsConstructor
public class MenuController {

    private final MenuService menuService;

    /** 当前用户菜单树（身份来自网关透传的 X-User-Id） */
    @GetMapping
    public ApiResult<List<MenuVO>> myMenus() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            return ApiResult.ok(List.of());
        }
        return ApiResult.ok(menuService.listMenusForUser(userId));
    }

    /** 全部菜单树（管理端） */
    @GetMapping("/tree")
    @PreAuthorize("hasAuthority('menu:list')")
    public ApiResult<List<MenuVO>> tree() {
        return ApiResult.ok(menuService.listAllTree());
    }

    /** 某角色已授权的菜单ID */
    @GetMapping("/role/{roleId}")
    @PreAuthorize("hasAuthority('menu:list')")
    public ApiResult<List<Long>> menuIdsOfRole(@PathVariable Long roleId) {
        return ApiResult.ok(menuService.listMenuIdsByRoleId(roleId));
    }

    /** 给角色授权菜单（全量覆盖） */
    @PostMapping("/role/{roleId}")
    @PreAuthorize("hasAuthority('menu:assign')")
    public ApiResult<Boolean> assignToRole(@PathVariable Long roleId, @RequestBody List<Long> menuIds) {
        menuService.assignMenusToRole(roleId, menuIds);
        return ApiResult.ok(true);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('menu:add')")
    public ApiResult<Boolean> create(@RequestBody Menu menu) {
        return ApiResult.ok(menuService.save(menu));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('menu:edit')")
    public ApiResult<Boolean> update(@PathVariable Long id, @RequestBody Menu menu) {
        menu.setId(id);
        return ApiResult.ok(menuService.updateById(menu));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('menu:delete')")
    public ApiResult<Boolean> remove(@PathVariable Long id) {
        return ApiResult.ok(menuService.removeById(id));
    }
}
