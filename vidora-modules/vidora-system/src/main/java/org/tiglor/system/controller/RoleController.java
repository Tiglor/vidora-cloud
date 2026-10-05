package org.tiglor.system.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.tiglor.common.core.ApiResult;
import org.tiglor.common.log.annotation.BusinessType;
import org.tiglor.common.log.annotation.OperLog;
import org.tiglor.common.redis.CacheNames;
import org.tiglor.common.user.entity.Role;
import org.tiglor.common.user.entity.UserRole;
import org.tiglor.common.user.mapper.RoleMapper;
import org.tiglor.common.user.mapper.UserRoleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 角色管理：查询角色、查看/分配用户角色（授权）
 */
@RestController
@RequestMapping("/roles")
@RequiredArgsConstructor
public class RoleController {

    private final RoleMapper roleMapper;
    private final UserRoleMapper userRoleMapper;

    @GetMapping
    @PreAuthorize("hasAuthority('role:list')")
    public ApiResult<List<Role>> list() {
        return ApiResult.ok(roleMapper.selectList(new LambdaQueryWrapper<Role>().eq(Role::getStatus, 1)));
    }

    /** 某用户拥有的角色ID */
    @GetMapping("/user/{userId}")
    @PreAuthorize("hasAuthority('role:list')")
    public ApiResult<List<Long>> roleIdsOfUser(@PathVariable Long userId) {
        return ApiResult.ok(userRoleMapper.selectList(new LambdaQueryWrapper<UserRole>()
                        .eq(UserRole::getUserId, userId))
                .stream().map(UserRole::getRoleId).toList());
    }

    /** 给用户分配角色（全量覆盖） */
    @PostMapping("/user/{userId}")
    @PreAuthorize("hasAuthority('role:assign')")
    @OperLog(title = "角色管理", type = BusinessType.GRANT)
    @Transactional(rollbackFor = Exception.class)
    // 角色变了菜单权限就变了，必须让该用户已缓存的菜单树失效
    @CacheEvict(cacheNames = CacheNames.MENU_USER_TREE, key = "#userId")
    public ApiResult<Boolean> assignToUser(@PathVariable Long userId, @RequestBody List<Long> roleIds) {
        userRoleMapper.delete(new LambdaQueryWrapper<UserRole>().eq(UserRole::getUserId, userId));
        if (roleIds != null) {
            LocalDateTime now = LocalDateTime.now();
            for (Long roleId : roleIds) {
                UserRole ur = new UserRole();
                ur.setUserId(userId);
                ur.setRoleId(roleId);
                ur.setCreateTime(now);
                userRoleMapper.insert(ur);
            }
        }
        return ApiResult.ok(true);
    }
}
