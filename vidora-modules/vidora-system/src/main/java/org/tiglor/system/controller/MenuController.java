package org.tiglor.system.controller;

import org.tiglor.common.core.ApiResult;
import org.tiglor.common.core.security.UserContext;
import org.tiglor.common.log.annotation.BusinessType;
import org.tiglor.common.log.annotation.OperLog;
import org.tiglor.system.entity.Menu;
import org.tiglor.system.service.MenuService;
import org.tiglor.system.vo.MenuVO;
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

    /**
     * 查询我的菜单树
     *
     * <p>当前登录用户的菜单树，身份来自网关透传的 X-User-Id。</p>
     */
    @GetMapping
    public ApiResult<List<MenuVO>> myMenus() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            return ApiResult.ok(List.of());
        }
        return ApiResult.ok(menuService.listMenusForUser(userId));
    }

    /**
     * 查询全部菜单树
     *
     * <p>管理端用，返回全部菜单节点拼成的树。</p>
     */
    @GetMapping("/tree")
    @PreAuthorize("hasAuthority('menu:list')")
    public ApiResult<List<MenuVO>> tree() {
        return ApiResult.ok(menuService.listAllTree());
    }

    /**
     * 查询角色已授权菜单
     *
     * <p>只返回菜单 id 列表，不返回菜单对象。</p>
     */
    @GetMapping("/role/{roleId}")
    @PreAuthorize("hasAuthority('menu:list')")
    public ApiResult<List<Long>> menuIdsOfRole(@PathVariable Long roleId) {
        return ApiResult.ok(menuService.listMenuIdsByRoleId(roleId));
    }

    /**
     * 给角色授权菜单
     *
     * <p>提交的是菜单 id 列表，对这个角色全量覆盖。</p>
     * <p>授权变更会影响所有持有该角色的用户，所以用户维度的菜单树缓存整体失效。</p>
     */
    @PostMapping("/role/{roleId}")
    @PreAuthorize("hasAuthority('menu:assign')")
    @OperLog(title = "菜单管理", type = BusinessType.GRANT)
    public ApiResult<Boolean> assignToRole(@PathVariable Long roleId, @RequestBody List<Long> menuIds) {
        menuService.assignMenusToRole(roleId, menuIds);
        return ApiResult.ok(true);
    }

    /**
     * 新建菜单
     *
     * <p>新增一个菜单/权限节点，写 sys_menu。</p>
     * <p>
     * {@code parentId} 不传即为顶级节点。菜单树是热数据、结果全在 Redis 里，
     * 这里写完会把全量树和所有用户的树整体失效，所以前端刷新就能看到，不用等缓存到期。
     */
    @PostMapping
    @PreAuthorize("hasAuthority('menu:add')")
    @OperLog(title = "菜单管理", type = BusinessType.INSERT)
    public ApiResult<Boolean> create(@RequestBody Menu menu) {
        return ApiResult.ok(menuService.save(menu));
    }

    /**
     * 修改菜单
     *
     * <p>修改一个菜单节点，以路径上的 {@code id} 为准（请求体里的 id 会被覆盖）。</p>
     * <p>
     * 只更新请求体中非 null 的列，想单独隐藏或停用就只传 {@code visible}/{@code status} 即可；
     * 但改了 {@code permissionCode} 就等于改了所有已授权角色能调的接口，同样会让全量树与用户树整体失效。
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('menu:edit')")
    @OperLog(title = "菜单管理", type = BusinessType.UPDATE)
    public ApiResult<Boolean> update(@PathVariable Long id, @RequestBody Menu menu) {
        menu.setId(id);
        return ApiResult.ok(menuService.updateById(menu));
    }

    /**
     * 删除菜单
     *
     * <p>删除一个菜单节点，是逻辑删除（{@code is_deleted} 置 1），角色的授权关联行不清理、只是查不到。</p>
     * <p>
     * 这里不校验有没有子节点：删掉父节点会把它整棵子树一起从菜单树上摘走，
     * 管理端刷新后那些路由同时消失。要收权建议删按钮级（menuType=3）的权限点，别删目录。
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('menu:delete')")
    @OperLog(title = "菜单管理", type = BusinessType.DELETE)
    public ApiResult<Boolean> remove(@PathVariable Long id) {
        return ApiResult.ok(menuService.removeById(id));
    }
}
