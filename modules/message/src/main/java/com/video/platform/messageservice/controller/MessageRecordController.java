package com.video.platform.messageservice.controller;

import com.video.platform.messageservice.entity.MessageRecord;
import com.video.platform.messageservice.service.MessageRecordService;
import com.video.platform.common.ApiResult;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/messages")
@RequiredArgsConstructor
public class MessageRecordController {

    private final MessageRecordService service;

    @GetMapping("/page")
    public ApiResult<Page<MessageRecord>> page(@RequestParam(defaultValue = "1") long current,
                                           @RequestParam(defaultValue = "10") long size) {
        return ApiResult.ok(service.page(new Page<>(current, size)));
    }

    @GetMapping("/{id}")
    public ApiResult<MessageRecord> getById(@PathVariable Long id) {
        return ApiResult.ok(service.getById(id));
    }

    @PostMapping
    public ApiResult<Boolean> save(@RequestBody MessageRecord entity) {
        return ApiResult.ok(service.save(entity));
    }

    @PutMapping("/{id}")
    public ApiResult<Boolean> update(@PathVariable Long id, @RequestBody MessageRecord entity) {
        entity.setId(id);
        return ApiResult.ok(service.updateById(entity));
    }

    @DeleteMapping("/{id}")
    public ApiResult<Boolean> remove(@PathVariable Long id) {
        return ApiResult.ok(service.removeById(id));
    }
}
