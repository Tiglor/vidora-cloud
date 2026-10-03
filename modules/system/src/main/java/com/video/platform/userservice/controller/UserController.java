package com.video.platform.userservice.controller;

import com.video.platform.userservice.entity.User;
import com.video.platform.userservice.service.UserService;
import com.video.platform.common.ApiResult;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService service;

    @GetMapping("/page")
    public ApiResult<Page<User>> page(@RequestParam(defaultValue = "1") long current,
                                           @RequestParam(defaultValue = "10") long size) {
        return ApiResult.ok(service.page(new Page<>(current, size)));
    }

    @GetMapping("/{id}")
    public ApiResult<User> getById(@PathVariable Long id) {
        return ApiResult.ok(service.getById(id));
    }

    @PostMapping
    public ApiResult<Boolean> save(@RequestBody User entity) {
        return ApiResult.ok(service.save(entity));
    }

    @PutMapping("/{id}")
    public ApiResult<Boolean> update(@PathVariable Long id, @RequestBody User entity) {
        entity.setId(id);
        return ApiResult.ok(service.updateById(entity));
    }

    @DeleteMapping("/{id}")
    public ApiResult<Boolean> remove(@PathVariable Long id) {
        return ApiResult.ok(service.removeById(id));
    }
}
