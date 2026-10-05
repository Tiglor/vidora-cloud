package org.tiglor.system.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.tiglor.common.core.ApiResult;
import org.tiglor.system.service.OperLogService;
import org.tiglor.system.vo.OperLogVO;

import java.time.LocalDateTime;

/**
 * 操作审计日志查询。
 * <p>
 * 刻意没有删除与清空接口：能被人随手清空的审计表等于没有审计表，
 * 「谁删了视频」和「谁删了那条记录」必须是两件事。保留期由归档任务管，不归管理端按钮管。
 */
@RestController
@RequestMapping("/oper-logs")
@RequiredArgsConstructor
public class OperLogController {

    private final OperLogService operLogService;

    @GetMapping("/page")
    @PreAuthorize("hasAuthority('operlog:list')")
    public ApiResult<Page<OperLogVO>> page(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) Integer businessType,
            @RequestParam(required = false) Long operUserId,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime beginTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        return ApiResult.ok(operLogService.pageLogs(current, size, title, businessType,
                operUserId, status, beginTime, endTime));
    }

    /** 单条详情：列表为了不把几千字的请求参数整页拖过来，故意不查大字段 */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('operlog:list')")
    public ApiResult<OperLogVO> detail(@PathVariable Long id) {
        return ApiResult.ok(operLogService.detail(id));
    }
}
