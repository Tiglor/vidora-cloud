package org.tiglor.system.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.tiglor.common.core.ApiResult;
import org.tiglor.common.core.BizException;
import org.tiglor.common.core.ResultCode;
import org.tiglor.common.log.config.LogProperties;
import org.tiglor.common.log.entity.LoginLogEntity;
import org.tiglor.common.log.entity.OperLogEntity;
import org.tiglor.common.log.sink.LogSink;
import org.tiglor.common.log.sink.RemoteLogSink;

/**
 * 审计日志的内网写入口。
 * <p>
 * 路径刻意用 {@code /internal/**} 而不是 {@code /api/**}：网关只路由 {@code /api} 前缀，
 * 所以这个口在架构上就是「只有容器网络内的服务能打到」，不需要用户身份。
 * 但要注意网关开了 discovery locator，理论上 {@code /system-service/internal/logs/oper}
 * 能被路由进来 —— 所以再加一道共享口令校验，配置了就必须带对，没配置只限本机开发。
 */
@RestController
@RequestMapping("/internal/logs")
@RequiredArgsConstructor
public class InternalLogController {

    private final LogSink logSink;
    private final LogProperties logProperties;

    @PostMapping("/oper")
    public ApiResult<Boolean> oper(@RequestHeader(value = RemoteLogSink.INGEST_TOKEN_HEADER, required = false) String token,
                                   @RequestBody OperLogEntity record) {
        requireToken(token);
        logSink.submit(record);
        return ApiResult.ok(true);
    }

    @PostMapping("/login")
    public ApiResult<Boolean> login(@RequestHeader(value = RemoteLogSink.INGEST_TOKEN_HEADER, required = false) String token,
                                    @RequestBody LoginLogEntity record) {
        requireToken(token);
        logSink.submit(record);
        return ApiResult.ok(true);
    }

    private void requireToken(String token) {
        String expected = logProperties.getIngestToken();
        if (expected == null || expected.isBlank()) {
            return;
        }
        if (!expected.equals(token)) {
            throw new BizException(ResultCode.FORBIDDEN, "审计写入口令不匹配");
        }
    }
}
