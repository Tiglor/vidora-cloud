package com.video.platform.authservice.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.video.platform.authservice.dto.UserLoginDTO;
import com.video.platform.authservice.dto.UserRegisterDTO;
import com.video.platform.authservice.entity.Menu;
import com.video.platform.authservice.entity.Role;
import com.video.platform.authservice.entity.RoleMenu;
import com.video.platform.authservice.entity.User;
import com.video.platform.authservice.entity.UserRole;
import com.video.platform.authservice.mapper.MenuMapper;
import com.video.platform.authservice.mapper.RoleMapper;
import com.video.platform.authservice.mapper.RoleMenuMapper;
import com.video.platform.authservice.mapper.UserMapper;
import com.video.platform.authservice.mapper.UserRoleMapper;
import com.video.platform.authservice.service.AuthService;
import com.video.platform.authservice.vo.LoginVO;
import com.video.platform.common.BizException;
import com.video.platform.common.JwtUtil;
import com.video.platform.common.ResultCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserMapper userMapper;
    private final UserRoleMapper userRoleMapper;
    private final RoleMapper roleMapper;
    private final RoleMenuMapper roleMenuMapper;
    private final MenuMapper menuMapper;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Override
    public LoginVO login(UserLoginDTO dto) {
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getPhone, dto.getPhone()));
        if (user == null) {
            throw new BizException(ResultCode.NOT_FOUND, "用户不存在");
        }
        if (!passwordEncoder.matches(dto.getPassword(), user.getPasswordHash())) {
            throw new BizException(ResultCode.FAIL, "密码错误");
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new BizException(ResultCode.FORBIDDEN, "账号已被禁用");
        }

        List<String> roles = loadRoleCodes(user.getId());
        List<String> permissions = loadPermissionCodes(user.getId());
        String token = JwtUtil.generateToken(user.getId(), String.join(",", roles), String.join(",", permissions));

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
    public Long register(UserRegisterDTO dto) {
        User exist = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getPhone, dto.getPhone()));
        if (exist != null) {
            throw new BizException(ResultCode.FAIL, "手机号已注册");
        }
        User user = new User();
        user.setPhone(dto.getPhone());
        user.setNickname(StringUtils.isNotBlank(dto.getNickname()) ? dto.getNickname() : dto.getPhone());
        user.setPasswordHash(passwordEncoder.encode(dto.getPassword()));
        user.setStatus(1);
        user.setFollowCount(0);
        user.setFollowerCount(0);
        userMapper.insert(user);

        // 新用户默认赋予普通用户角色（role_id=2）。
        try {
            UserRole userRole = new UserRole();
            userRole.setUserId(user.getId());
            userRole.setRoleId(2L);
            userRole.setCreateTime(LocalDateTime.now());
            userRoleMapper.insert(userRole);
        } catch (Exception e) {
            log.warn("为新用户分配默认角色失败，userId={}", user.getId(), e);
        }
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
