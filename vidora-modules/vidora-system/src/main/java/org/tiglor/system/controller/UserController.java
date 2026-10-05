package org.tiglor.system.controller;

import org.tiglor.common.user.entity.User;
import org.tiglor.system.service.UserService;
import org.tiglor.common.core.ApiResult;
import org.tiglor.common.log.annotation.BusinessType;
import org.tiglor.common.log.annotation.OperLog;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService service;

    @GetMapping("/page")
    @PreAuthorize("hasAuthority('user:list')")
    public ApiResult<Page<User>> page(@RequestParam(defaultValue = "1") long current,
                                      @RequestParam(defaultValue = "10") long size,
                                      @RequestParam(required = false) String phone,
                                      @RequestParam(required = false) String nickname,
                                      @RequestParam(required = false) Integer status) {
        return ApiResult.ok(service.pageUsers(current, size, phone, nickname, status));
    }

    /**
     * 单个用户详情。**这里刻意不加 {@code user:list}**：
     * video-service 的 {@code GET /videos/{id}/owner} 会用「浏览者自己的 token」Feign 调到这里，
     * 普通用户在移动端看视频详情页也要拿 UP 主昵称头像。加上的话移动端那屏直接 403。
     * 出口只有 {@code RemoteUserDTO} 那几个字段，且网关已经把 {@code /api/users/**} 挡在管理端之外。
     */
    @GetMapping("/{id}")
    public ApiResult<User> getById(@PathVariable Long id) {
        return ApiResult.ok(service.getById(id));
    }

    /**
     * 直接落实体：{@code password_hash} 是 NOT NULL 且这里不会做 BCrypt，
     * 所以这个接口建不出能登录的账号。注册走 auth-service 的 {@code /auth/register}。
     * 留着是为了服务内部需要手工插一条用户记录的场景，不给管理端界面用。
     */
    @PostMapping
    @PreAuthorize("hasAuthority('user:add')")
    @OperLog(title = "用户管理", type = BusinessType.INSERT)
    public ApiResult<Boolean> save(@RequestBody User entity) {
        return ApiResult.ok(service.save(entity));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('user:edit')")
    @OperLog(title = "用户管理", type = BusinessType.UPDATE)
    public ApiResult<Boolean> update(@PathVariable Long id, @RequestBody User entity) {
        entity.setId(id);
        return ApiResult.ok(service.updateById(entity));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('user:delete')")
    @OperLog(title = "用户管理", type = BusinessType.DELETE)
    public ApiResult<Boolean> remove(@PathVariable Long id) {
        return ApiResult.ok(service.removeById(id));
    }
}
