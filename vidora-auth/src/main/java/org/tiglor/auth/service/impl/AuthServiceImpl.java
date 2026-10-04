package org.tiglor.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import org.tiglor.auth.dto.UserLoginDTO;
import org.tiglor.auth.dto.UserRegisterDTO;
import org.tiglor.common.user.entity.Menu;
import org.tiglor.common.user.entity.Role;
import org.tiglor.common.user.entity.RoleMenu;
import org.tiglor.common.user.entity.User;
import org.tiglor.common.user.entity.UserRole;
import org.tiglor.common.user.mapper.MenuMapper;
import org.tiglor.common.user.mapper.RoleMapper;
import org.tiglor.common.user.mapper.RoleMenuMapper;
import org.tiglor.common.user.mapper.UserMapper;
import org.tiglor.common.user.mapper.UserRoleMapper;
import org.tiglor.auth.service.AuthService;
import org.tiglor.auth.vo.LoginVO;
import org.tiglor.common.core.BizException;
import org.tiglor.common.core.JwtUtil;
import org.tiglor.common.core.ResultCode;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    /** 注册时默认赋予的普通用户角色，对应 SQL 初始化脚本中的 sys_role.id */
    private static final Long DEFAULT_ROLE_ID = 2L;

    private final UserMapper userMapper;
    private final UserRoleMapper userRoleMapper;
    private final RoleMapper roleMapper;
    private final RoleMenuMapper roleMenuMapper;
    private final MenuMapper menuMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    @Override
    public LoginVO login(UserLoginDTO dto) {
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getPhone, dto.getPhone()));
        // 账号不存在与密码错误返回同一提示，避免手机号枚举
        if (user == null || !passwordEncoder.matches(dto.getPassword(), user.getPasswordHash())) {
            throw new BizException(ResultCode.UNAUTHORIZED, "手机号或密码错误");
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new BizException(ResultCode.FORBIDDEN, "账号已被禁用");
        }

        List<String> roles = loadRoleCodes(user.getId());
        List<String> permissions = loadPermissionCodes(user.getId());
        String token = jwtUtil.generateToken(user.getId(), String.join(",", roles), String.join(",", permissions));

        LoginVO vo = new LoginVO();
        vo.setToken(token);
        vo.setUserId(user.getId());
        vo.setNickname(user.getNickname());
        vo.setAvatarUrl(user.getAvatarUrl());
        vo.setRoles(roles);
        vo.setPermissions(permissions);
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long register(UserRegisterDTO dto) {
        User exist = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getPhone, dto.getPhone()));
        if (exist != null) {
            throw new BizException(ResultCode.VALIDATE_FAILED, "手机号已注册");
        }
        User user = new User();
        user.setPhone(dto.getPhone());
        user.setNickname(StringUtils.isNotBlank(dto.getNickname()) ? dto.getNickname() : dto.getPhone());
        user.setPasswordHash(passwordEncoder.encode(dto.getPassword()));
        user.setStatus(1);
        user.setFollowCount(0);
        user.setFollowerCount(0);
        userMapper.insert(user);

        // 新用户默认赋予普通用户角色（role_id=2）
        UserRole userRole = new UserRole();
        userRole.setUserId(user.getId());
        userRole.setRoleId(DEFAULT_ROLE_ID);
        userRole.setCreateTime(LocalDateTime.now());
        userRoleMapper.insert(userRole);
        return user.getId();
    }

    private List<String> loadRoleCodes(Long userId) {
        List<Long> roleIds = listRoleIds(userId);
        if (roleIds.isEmpty()) {
            return List.of();
        }
        return roleMapper.selectBatchIds(roleIds).stream()
                .map(Role::getRoleCode)
                .filter(StringUtils::isNotBlank)
                .distinct()
                .toList();
    }

    private List<String> loadPermissionCodes(Long userId) {
        List<Long> roleIds = listRoleIds(userId);
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
                .filter(StringUtils::isNotBlank)
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
}
