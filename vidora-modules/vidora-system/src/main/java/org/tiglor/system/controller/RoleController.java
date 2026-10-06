package org.tiglor.system.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.tiglor.common.core.ApiResult;
import org.tiglor.common.log.annotation.BusinessType;
import org.tiglor.common.log.annotation.OperLog;
import org.tiglor.common.redis.CacheNames;
import org.tiglor.system.entity.Role;
import org.tiglor.system.entity.UserRole;
import org.tiglor.system.mapper.RoleMapper;
import org.tiglor.system.mapper.UserRoleMapper;
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

    /**
     * 查询可分配角色列表
     *
     * <p>列出可分配的角色，只回 {@code status=1} 的启用角色，被停用的不出现在这里。</p>
     * <p>
     * 供「给用户分配角色」的下拉使用，所以刻意不返回全量；也没有分页与排序参数。
     *
     * @return 角色数组（非分页对象），已被停用角色不在其中
     */
    @GetMapping
    @PreAuthorize("hasAuthority('role:list')")
    public ApiResult<List<Role>> list() {
        return ApiResult.ok(roleMapper.selectList(new LambdaQueryWrapper<Role>().eq(Role::getStatus, 1)));
    }

    /**
     * 查询用户已有角色
     *
     * <p>只返回角色 id 列表，不返回角色对象。</p>
     */
    @GetMapping("/user/{userId}")
    @PreAuthorize("hasAuthority('role:list')")
    public ApiResult<List<Long>> roleIdsOfUser(@PathVariable Long userId) {
        return ApiResult.ok(userRoleMapper.selectList(new LambdaQueryWrapper<UserRole>()
                        .eq(UserRole::getUserId, userId))
                .stream().map(UserRole::getRoleId).toList());
    }

    /**
     * 给用户分配角色
     *
     * <p>提交的是角色 id 列表，对该用户全量覆盖。</p>
     */
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
