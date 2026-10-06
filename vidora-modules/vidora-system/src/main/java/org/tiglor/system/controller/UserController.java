package org.tiglor.system.controller;

import org.tiglor.system.entity.User;
import org.tiglor.system.service.UserService;
import org.tiglor.common.core.ApiResult;
import org.tiglor.common.log.annotation.BusinessType;
import org.tiglor.common.log.annotation.OperLog;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 用户管理：sys_user 表，一个账号一行，这里是给管理端后台用的增删改查。
 * <p>
 * 边界要说清：认证相关的都归 auth-service（{@code /auth/register} 签发 token、个人中心改资料走 profile 接口），
 * 这个 controller 只负责运营侧看人和改人，所以 {@code passwordHash} 不会随响应输出、也不在这里被改写。
 */
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService service;

    /**
     * 用户分页
     *
     * <p>管理端查用户，可按手机号/昵称模糊、按状态精确筛，新注册的排最前。</p>
     * <p>
     * {@code size} 上限 100，传更大也只给 100 —— 不然一次请求就能把整张 sys_user 拉进内存；
     * 空白的筛选值会当成「不加这个条件」。排序用 create_time 倒序并以 id 兜底，同一秒注册的用户翻页时才不会串行或重复。
     * 返回的是实体本身，{@code passwordHash} 只读不写、不会出现在响应里。
     *
     * @param status 账号状态：0-禁用，1-正常；不传则两种都要
     *
     * @return 分页对象（records / total / current），不是裸数组
     */
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
     * 查询用户详情
     *
     * <p>刻意不加 {@code user:list} 权限点。</p>
     * <p>
     * video-service 的 {@code GET /videos/{id}/owner} 会用「浏览者自己的 token」Feign 调到这里，
     * 普通用户在移动端看视频详情页也要拿 UP 主昵称头像。加上的话移动端那屏直接 403。
     * 出口只有 {@code RemoteUserDTO} 那几个字段，且网关已经把 {@code /api/users/**} 挡在管理端之外。
     */
    @GetMapping("/{id}")
    public ApiResult<User> getById(@PathVariable Long id) {
        return ApiResult.ok(service.getById(id));
    }

    /**
     * 新建用户
     *
     * <p>这个接口直接落实体。</p>
     * <p>
     * {@code password_hash} 是 NOT NULL 且这里不会做 BCrypt，
     * 所以这个接口建不出能登录的账号。注册走 auth-service 的 {@code /auth/register}。
     * 留着是为了服务内部需要手工插一条用户记录的场景，不给管理端界面用。
     */
    @PostMapping
    @PreAuthorize("hasAuthority('user:add')")
    @OperLog(title = "用户管理", type = BusinessType.INSERT)
    public ApiResult<Boolean> save(@RequestBody User entity) {
        return ApiResult.ok(service.save(entity));
    }

    /**
     * 修改用户
     *
     * <p>修改一个用户的资料，以路径上的 {@code id} 为准（请求体里的 id 会被覆盖）。</p>
     * <p>
     * 只更新请求体中非 null 的列，运营侧禁用/恢复账号就只传 {@code status}（0-禁用 1-正常）。
     * 注意这里不会做 BCrypt：{@code passwordHash} 传进来是什么就存什么，改密码请走认证侧。
     * {@code followCount}/{@code followerCount} 目前是死数据：{@code user_follow} 表在 Java 侧没有任何读写方，
     * 两列自注册置 0 后不再变化，传值只会把 0 改成一个对不上的数。
     * {@code email} / {@code gender} / {@code birthday} / {@code bio} / {@code region} / {@code avatarUrl}
     * 也只有这里能写——观众侧自助接口只开放主题一项。
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('user:edit')")
    @OperLog(title = "用户管理", type = BusinessType.UPDATE)
    public ApiResult<Boolean> update(@PathVariable Long id, @RequestBody User entity) {
        entity.setId(id);
        return ApiResult.ok(service.updateById(entity));
    }

    /**
     * 删除用户
     *
     * <p>删除一个用户，是逻辑删除（{@code is_deleted} 置 1），数据行仍留在库里。</p>
     * <p>
     * 不做任何级联：他名下的视频、关注关系、评论都不会被清理，历史审计日志里的 userId 也照旧指向这条记录。
     * 只是不想让某个账号再登录的话，应该用 {@code status=0} 禁用而不是删。
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('user:delete')")
    @OperLog(title = "用户管理", type = BusinessType.DELETE)
    public ApiResult<Boolean> remove(@PathVariable Long id) {
        return ApiResult.ok(service.removeById(id));
    }
}
