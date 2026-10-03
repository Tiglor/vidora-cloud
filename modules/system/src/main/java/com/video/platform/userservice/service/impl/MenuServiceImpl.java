package com.video.platform.userservice.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.video.platform.userservice.entity.Menu;
import com.video.platform.userservice.entity.RoleMenu;
import com.video.platform.userservice.entity.UserRole;
import com.video.platform.userservice.mapper.MenuMapper;
import com.video.platform.userservice.mapper.RoleMenuMapper;
import com.video.platform.userservice.mapper.UserRoleMapper;
import com.video.platform.userservice.service.MenuService;
import com.video.platform.userservice.vo.MenuVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 菜单与授权：用户 → 角色 → 菜单/权限
 */
@Service
@RequiredArgsConstructor
public class MenuServiceImpl extends ServiceImpl<MenuMapper, Menu> implements MenuService {

    private final UserRoleMapper userRoleMapper;
    private final RoleMenuMapper roleMenuMapper;

    @Override
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
    public List<MenuVO> listAllTree() {
        List<Menu> menus = lambdaQuery()
                .orderByAsc(Menu::getSortOrder)
                .list();
        return buildTree(menus);
    }

    @Override
    public List<Long> listMenuIdsByRoleId(Long roleId) {
        return roleMenuMapper.selectList(new LambdaQueryWrapper<RoleMenu>()
                        .eq(RoleMenu::getRoleId, roleId))
                .stream()
                .map(RoleMenu::getMenuId)
                .distinct()
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignMenusToRole(Long roleId, List<Long> menuIds) {
        // 全量覆盖：先删后插
        roleMenuMapper.delete(new LambdaQueryWrapper<RoleMenu>().eq(RoleMenu::getRoleId, roleId));
        if (menuIds == null || menuIds.isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        List<RoleMenu> list = new ArrayList<>();
        for (Long menuId : menuIds) {
            RoleMenu rm = new RoleMenu();
            rm.setRoleId(roleId);
            rm.setMenuId(menuId);
            rm.setCreateTime(now);
            list.add(rm);
        }
        // 批量插入
        for (RoleMenu rm : list) {
            roleMenuMapper.insert(rm);
        }
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
        return v;
    }
}
