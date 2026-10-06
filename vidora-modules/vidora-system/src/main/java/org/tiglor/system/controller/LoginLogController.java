package org.tiglor.system.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.tiglor.common.core.ApiResult;
import org.tiglor.common.log.entity.LoginLogEntity;
import org.tiglor.system.service.LoginLogService;

import java.time.LocalDateTime;

/** 登录日志查询。与操作日志一样只读：失败登录记录是判断爆破的依据，不能被清掉。 */
@RestController
@RequestMapping("/login-logs")
@RequiredArgsConstructor
public class LoginLogController {

    private final LoginLogService loginLogService;

    /**
     * 登录日志分页
     *
     * <p>管理端查登录记录，可按账号、成败、来源端与时间区间筛，最新的排最前。</p>
     * <p>
     * {@code status} 说的是「这一次登录成没成」，不是账号被没被禁用；空串筛选条件会归一成不加条件。
     *
     * @param status    登录结果：0-失败，1-成功；不传则两种都要
     * @param clientKey 来源端标识 web / mobile / admin，精确匹配而非模糊
     * @param beginTime 起始时间（含），ISO-8601 日期时间格式，如 2024-06-01T00:00:00
     * @param endTime   截止时间（含），格式同上；两端可单独使用
     *
     * @return 分页对象，records 为 {@code sys_login_log} 整行（含 UA 原文与失败原因 msg）
     */
    @GetMapping("/page")
    @PreAuthorize("hasAuthority('loginlog:list')")
    public ApiResult<Page<LoginLogEntity>> page(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String clientKey,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime beginTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        return ApiResult.ok(loginLogService.pageLogs(current, size, username, status,
                clientKey, beginTime, endTime));
    }
}
