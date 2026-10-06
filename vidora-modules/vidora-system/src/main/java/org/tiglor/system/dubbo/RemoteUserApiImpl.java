package org.tiglor.system.dubbo;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Transactional;
import org.tiglor.api.system.RemoteUserApi;
import org.tiglor.api.system.dto.RemoteLoginUserDTO;
import org.tiglor.api.system.dto.RemoteRegisterDTO;
import org.tiglor.system.entity.Menu;
import org.tiglor.system.entity.Role;
import org.tiglor.system.entity.RoleMenu;
import org.tiglor.system.entity.User;
import org.tiglor.system.entity.UserRole;
import org.tiglor.system.mapper.MenuMapper;
import org.tiglor.system.mapper.RoleMapper;
import org.tiglor.system.mapper.RoleMenuMapper;
import org.tiglor.system.mapper.UserMapper;
import org.tiglor.system.mapper.UserRoleMapper;

/**
 * {@link RemoteUserApi} 的 Dubbo provider。
 * <p>
 * 这些查询原先住在 auth-service（两边共用 {@code common-user} 的实体与 Mapper），搬过来的理由是
 * 「谁拥有表，谁拥有对表的读法」：sys_user / sys_role / sys_menu 的列一改，只编译到本服务，
 * 认证服务不再被表结构连坐。代价是登录链路上多一次 RPC，见 .code/ARCHITECTURE.md 6.2.1。
 * <p>
 * 全部方法都不抛业务异常，「查不到 / 撞车」用 {@code null} 表达——理由写在契约接口的类注释里。
 */
@Slf4j
@DubboService
@RequiredArgsConstructor
public class RemoteUserApiImpl implements RemoteUserApi {

    /** 注册时默认赋予的普通用户角色，对应 SQL/vidora_cloud.sql 里 sys_role 的种子 id */
    private static final Long DEFAULT_ROLE_ID = 2L;

    private final UserMapper userMapper;
    private final UserRoleMapper userRoleMapper;
    private final RoleMapper roleMapper;
    private final RoleMenuMapper roleMenuMapper;
    private final MenuMapper menuMapper;

    @Override
    public RemoteLoginUserDTO getLoginUser(String phone) {
        if (isBlank(phone)) {
            return null;
        }
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getPhone, phone));
        if (user == null) {
            return null;
        }

        RemoteLoginUserDTO dto = new RemoteLoginUserDTO();
        dto.setId(user.getId());
        dto.setNickname(user.getNickname());
        dto.setAvatarUrl(user.getAvatarUrl());
        dto.setStatus(user.getStatus());
        dto.setPasswordHash(user.getPasswordHash());
        dto.setThemeKey(user.getThemeKey());

        // 角色与权限在同一次调用里带回去：拆开就是每次登录多两个网络往返，
        // 而这三份数据本来就在同一个库、同一个事务视图里
        List<Long> roleIds = listRoleIds(user.getId());
        dto.setRoles(loadRoleCodes(roleIds));
        dto.setPermissions(loadPermissionCodes(roleIds));
        return dto;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long register(RemoteRegisterDTO register) {
        if (register == null || isBlank(register.getPhone()) || isBlank(register.getPasswordHash())) {
            return null;
        }
        User exist = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getPhone, register.getPhone()));
        if (exist != null) {
            return null;
        }

        User user = new User();
        user.setPhone(register.getPhone());
        // 昵称空就用手机号兜底：sys_user.nickname 是 NOT NULL，而注册表单允许不填
        user.setNickname(isBlank(register.getNickname()) ? register.getPhone() : register.getNickname());
        user.setPasswordHash(register.getPasswordHash());
        user.setStatus(1);
        user.setFollowCount(0);
        user.setFollowerCount(0);
        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException e) {
            // 先查后插之间的并发窗口：唯一索引是真正的守卫，这里把撞车翻译成和「已存在」同一个返回值，
            // 调用方就不必区分两种失败。但一定要留一条日志：撞的未必是 uk_phone，
            // 静默返回 null 会让「其实是别的约束炸了」这种真因永远查不出来
            log.warn("注册撞唯一键，按已存在处理 phone={}", register.getPhone(), e);
            return null;
        }

        UserRole userRole = new UserRole();
        userRole.setUserId(user.getId());
        userRole.setRoleId(DEFAULT_ROLE_ID);
        userRole.setCreateTime(LocalDateTime.now());
        userRoleMapper.insert(userRole);
        return user.getId();
    }

    @Override
    public void updateTheme(Long userId, String themeKey) {
        if (userId == null) {
            return;
        }
        // 只更新这一列，而不是 updateById(entity)：后者会把整个实体带上去，
        // 哪天 User 加了字段，这里就成了「用户能改自己任意资料」的越权口子
        userMapper.update(null, Wrappers.<User>lambdaUpdate()
                .eq(User::getId, userId)
                .set(User::getThemeKey, themeKey));
    }

    private List<String> loadRoleCodes(List<Long> roleIds) {
        if (roleIds.isEmpty()) {
            return List.of();
        }
        return roleMapper.selectBatchIds(roleIds).stream()
                .map(Role::getRoleCode)
                .filter(code -> !isBlank(code))
                .distinct()
                .toList();
    }

    private List<String> loadPermissionCodes(List<Long> roleIds) {
        if (roleIds.isEmpty()) {
            return List.of();
        }
        List<Long> menuIds = roleMenuMapper.selectList(new LambdaQueryWrapper<RoleMenu>()
                        .in(RoleMenu::getRoleId, roleIds)).stream()
                .map(RoleMenu::getMenuId)
                .distinct()
                .toList();
        if (menuIds.isEmpty()) {
            return List.of();
        }
        return menuMapper.selectBatchIds(menuIds).stream()
                .map(Menu::getPermissionCode)
                .filter(code -> !isBlank(code))
                .distinct()
                .toList();
    }

    private List<Long> listRoleIds(Long userId) {
        return userRoleMapper.selectList(new LambdaQueryWrapper<UserRole>()
                        .eq(UserRole::getUserId, userId)).stream()
                .map(UserRole::getRoleId)
                .distinct()
                .toList();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
