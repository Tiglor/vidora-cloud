package org.tiglor.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tiglor.common.core.BizException;
import org.tiglor.common.core.ResultCode;
import org.tiglor.common.redis.CacheNames;
import org.tiglor.system.entity.Menu;
import org.tiglor.system.entity.RoleMenu;
import org.tiglor.system.entity.UserRole;
import org.tiglor.system.mapper.MenuMapper;
import org.tiglor.system.mapper.RoleMenuMapper;
import org.tiglor.system.mapper.UserRoleMapper;
import org.tiglor.system.service.MenuService;
import org.tiglor.system.vo.MenuVO;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 菜单与授权：用户 → 角色 → 菜单/权限
 * <p>
 * 菜单树是「每次页面加载都要查、但极少改动」的典型热数据，三个查询都走 Redis 缓存；
 * 任何会改变结果的写入（菜单增删改、给角色重新授权）都要精确失效对应缓存。
 * </p>
 */
@Service
@RequiredArgsConstructor
public class MenuServiceImpl extends ServiceImpl<MenuMapper, Menu> implements MenuService {

    private final UserRoleMapper userRoleMapper;
    private final RoleMenuMapper roleMenuMapper;

    @Override
    @Cacheable(cacheNames = CacheNames.MENU_USER_TREE, key = "#userId")
    public List<MenuVO> listMenusForUser(Long userId) {
        List<Long> roleIds = listRoleIdsByUser(userId);
        if (roleIds.isEmpty()) {
            return List.of();
        }
        List<Long> menuIds = listMenuIdsByRoleIds(roleIds);
        if (menuIds.isEmpty()) {
            return List.of();
        }
        List<Menu> menus = lambdaQuery()
                .in(Menu::getId, menuIds)
                .in(Menu::getMenuType, List.of(1, 2))
                .eq(Menu::getVisible, 1)
                .eq(Menu::getStatus, 1)
                .orderByAsc(Menu::getSortOrder)
                .list();
        return buildTree(menus);
    }

    @Override
    @Cacheable(cacheNames = CacheNames.MENU_ALL_TREE, key = "'all'")
    public List<MenuVO> listAllTree() {
        List<Menu> menus = lambdaQuery()
                .orderByAsc(Menu::getSortOrder)
                .list();
        return buildTree(menus);
    }

    @Override
    @Cacheable(cacheNames = CacheNames.MENU_ROLE_IDS, key = "#roleId")
    public List<Long> listMenuIdsByRoleId(Long roleId) {
        return listMenuIdsByRoleIds(List.of(roleId));
    }

    @Override
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.MENU_ROLE_IDS, key = "#roleId"),
            // 授权变更会影响所有持有该角色的用户，用户维度只能整体失效
            @CacheEvict(cacheNames = CacheNames.MENU_USER_TREE, allEntries = true)
    })
    @Transactional(rollbackFor = Exception.class)
    public void assignMenusToRole(Long roleId, List<Long> menuIds) {
        // 全量覆盖：先删后插
        roleMenuMapper.delete(new LambdaQueryWrapper<RoleMenu>().eq(RoleMenu::getRoleId, roleId));
        if (menuIds == null || menuIds.isEmpty()) {
            return;
        }
        // 去重：uk_role_menu(role_id, menu_id) 是唯一键，同一个 id 传两次会在第二次插入时炸 500
        List<Long> targetIds = menuIds.stream().distinct().toList();
        // 存在性校验：sys_role_menu 上没有外键，不校验就能插进指向不存在菜单的脏行，
        // 之后授权树里少一块却查不出原因。listByIds 带逻辑删除过滤，
        // 所以已删除的菜单同样授不出去——这是想要的结果，不是副作用
        List<Long> existingIds = listByIds(targetIds).stream().map(Menu::getId).toList();
        if (existingIds.size() != targetIds.size()) {
            List<Long> missing = targetIds.stream().filter(id -> !existingIds.contains(id)).toList();
            throw new BizException(ResultCode.VALIDATE_FAILED, "菜单不存在或已删除：" + missing);
        }
        LocalDateTime now = LocalDateTime.now();
        for (Long menuId : targetIds) {
            RoleMenu rm = new RoleMenu();
            rm.setRoleId(roleId);
            rm.setMenuId(menuId);
            rm.setCreateTime(now);
            roleMenuMapper.insert(rm);
        }
    }

    // ---------- 菜单自身的增删改：全量树与所有用户树都失效 ----------

    @Override
    @CacheEvict(cacheNames = {CacheNames.MENU_ALL_TREE, CacheNames.MENU_USER_TREE}, allEntries = true)
    public boolean save(Menu entity) {
        return super.save(entity);
    }

    @Override
    @CacheEvict(cacheNames = {CacheNames.MENU_ALL_TREE, CacheNames.MENU_USER_TREE}, allEntries = true)
    public boolean updateById(Menu entity) {
        return super.updateById(entity);
    }

    @Override
    @CacheEvict(cacheNames = {CacheNames.MENU_ALL_TREE, CacheNames.MENU_USER_TREE}, allEntries = true)
    public boolean removeById(Serializable id) {
        return super.removeById(id);
    }

    // ---------- 内部工具 ----------

    private List<Long> listRoleIdsByUser(Long userId) {
        return userRoleMapper.selectList(new LambdaQueryWrapper<UserRole>()
                        .eq(UserRole::getUserId, userId))
                .stream()
                .map(UserRole::getRoleId)
                .distinct()
                .toList();
    }

    private List<Long> listMenuIdsByRoleIds(List<Long> roleIds) {
        if (roleIds.isEmpty()) {
            return List.of();
        }
        return roleMenuMapper.selectList(new LambdaQueryWrapper<RoleMenu>()
                        .in(RoleMenu::getRoleId, roleIds))
                .stream()
                .map(RoleMenu::getMenuId)
                .distinct()
                .toList();
    }

    /** 扁平列表 → 树 */
    private List<MenuVO> buildTree(List<Menu> menus) {
        List<MenuVO> all = menus.stream().map(this::toVO).toList();
        Map<Long, List<MenuVO>> byParent = all.stream()
                .collect(Collectors.groupingBy(m -> m.getParentId() == null ? 0L : m.getParentId()));

        for (MenuVO node : all) {
            List<MenuVO> children = byParent.getOrDefault(node.getId(), List.of());
            node.setChildren(children.stream()
                    .sorted(Comparator.comparing(MenuVO::getSortOrder, Comparator.nullsLast(Comparator.naturalOrder())))
                    .toList());
        }
        return byParent.getOrDefault(0L, List.of()).stream()
                .sorted(Comparator.comparing(MenuVO::getSortOrder, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    private MenuVO toVO(Menu m) {
        MenuVO v = new MenuVO();
        v.setId(m.getId());
        v.setParentId(m.getParentId());
        v.setMenuName(m.getMenuName());
        v.setMenuType(m.getMenuType());
        v.setPath(m.getPath());
        v.setComponent(m.getComponent());
        v.setIcon(m.getIcon());
        v.setSortOrder(m.getSortOrder());
        v.setPermissionCode(m.getPermissionCode());
        v.setVisible(m.getVisible());
        v.setStatus(m.getStatus());
        return v;
    }
}
