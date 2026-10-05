package org.tiglor.system.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.tiglor.common.core.ApiResult;
import org.tiglor.common.log.annotation.BusinessType;
import org.tiglor.common.log.annotation.OperLog;
import org.tiglor.common.user.entity.Client;
import org.tiglor.system.service.ClientService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/clients")
@RequiredArgsConstructor
public class ClientController {

    private final ClientService clientService;

    @GetMapping("/page")
    @PreAuthorize("hasAuthority('system:client:list')")
    public ApiResult<Page<Client>> page(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size) {
        return ApiResult.ok(clientService.page(new Page<>(current, size),
                new LambdaQueryWrapper<Client>().orderByDesc(Client::getId)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('system:client:list')")
    public ApiResult<Client> getById(@PathVariable Long id) {
        return ApiResult.ok(clientService.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('system:client:add')")
    @OperLog(title = "客户端管理", type = BusinessType.INSERT)
    public ApiResult<Boolean> save(@RequestBody Client client) {
        return ApiResult.ok(clientService.save(client));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('system:client:edit')")
    @OperLog(title = "客户端管理", type = BusinessType.UPDATE)
    public ApiResult<Boolean> update(@PathVariable Long id, @RequestBody Client client) {
        client.setId(id);
        return ApiResult.ok(clientService.updateById(client));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('system:client:delete')")
    @OperLog(title = "客户端管理", type = BusinessType.DELETE)
    public ApiResult<Boolean> delete(@PathVariable Long id) {
        return ApiResult.ok(clientService.removeById(id));
    }
}
