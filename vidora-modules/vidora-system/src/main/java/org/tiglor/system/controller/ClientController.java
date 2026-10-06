package org.tiglor.system.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.tiglor.common.core.ApiResult;
import org.tiglor.common.log.annotation.BusinessType;
import org.tiglor.common.log.annotation.OperLog;
import org.tiglor.system.entity.Client;
import org.tiglor.system.service.ClientService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 客户端（接入端）配置管理：sys_client 表，一行代表一个能登录的端（web / mobile / admin）。
 * <p>
 * 这里配的 {@code timeout} 就是该端签发 token 的固定过期秒数，auth-service 登录时按
 * {@code clientId} 查这张表决定放行哪些认证方式；{@code client_id} 与 {@code client_key}
 * 都有唯一键，改错一端会让那个端整体登不进来，所以只有管理端权限点能写。
 */
@RestController
@RequestMapping("/clients")
@RequiredArgsConstructor
public class ClientController {

    private final ClientService clientService;

    /**
     * 客户端分页
     *
     * <p>列出全部客户端配置，按 id 倒序（最近新增的排最前）。</p>
     * <p>
     * 这一页没有筛选项：端的数量是个位数，翻页翻完就行。
     *
     * @return MyBatis-Plus 分页对象（records / total / current），不是裸数组
     */
    @GetMapping("/page")
    @PreAuthorize("hasAuthority('system:client:list')")
    public ApiResult<Page<Client>> page(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size) {
        return ApiResult.ok(clientService.page(new Page<>(current, size),
                new LambdaQueryWrapper<Client>().orderByDesc(Client::getId)));
    }

    /**
     * 查询客户端详情
     *
     * <p>按主键 id 查单条客户端配置，鉴权与列表同一个权限点。</p>
     *
     * @return 记录不存在时 data 为 {@code null}，不会返回 404
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('system:client:list')")
    public ApiResult<Client> getById(@PathVariable Long id) {
        return ApiResult.ok(clientService.getById(id));
    }

    /**
     * 新建客户端
     *
     * <p>新增一条客户端配置。</p>
     * <p>
     * {@code id} 走数据库自增、{@code createTime}/{@code updateTime} 由服务端填充，请求体里传了也不生效；
     * {@code clientId} 与 {@code clientKey} 都是唯一键，撞号会直接失败而不是覆盖旧端。
     */
    @PostMapping
    @PreAuthorize("hasAuthority('system:client:add')")
    @OperLog(title = "客户端管理", type = BusinessType.INSERT)
    public ApiResult<Boolean> save(@RequestBody Client client) {
        return ApiResult.ok(clientService.save(client));
    }

    /**
     * 修改客户端
     *
     * <p>修改一条客户端配置，改完立即影响该端后续签发的 token（已签出的不受影响）。</p>
     * <p>
     * 以路径上的 {@code id} 为准，请求体里的 {@code id} 会被覆盖；
     * 更新只拼请求体中非 null 的列，因此想单独停用某端可以只传 {@code status=0}，其余字段不会被抹掉。
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('system:client:edit')")
    @OperLog(title = "客户端管理", type = BusinessType.UPDATE)
    public ApiResult<Boolean> update(@PathVariable Long id, @RequestBody Client client) {
        client.setId(id);
        return ApiResult.ok(clientService.updateById(client));
    }

    /**
     * 删除客户端
     *
     * <p>删除一条客户端配置，对应端随即无法登录。</p>
     * <p>
     * 是逻辑删除（{@code is_deleted} 置 1，后续查询自动过滤），历史 token 与已落库的日志都还在；
     * 但 {@code client_id} 上有唯一键，想「恢复」只能改回原记录或换一个 clientId。
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('system:client:delete')")
    @OperLog(title = "客户端管理", type = BusinessType.DELETE)
    public ApiResult<Boolean> delete(@PathVariable Long id) {
        return ApiResult.ok(clientService.removeById(id));
    }
}
