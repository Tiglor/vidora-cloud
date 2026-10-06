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
 * 路径刻意用 {@code /internal/「} 而不是 {@code /api/」}：网关只路由 {@code /api} 前缀，
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

    /**
     * 上报操作审计
     *
     * <p>接收其它服务上报的一条操作审计并落库。</p>
     * <p>
     * 请求体即 {@code sys_oper_log} 一行的字段（{@code operParam}/{@code jsonResult} 已在调用方截断脱敏），
     * 服务端不再校验内容也不去重，重复上报就会多出重复行。
     *
     * @param token 请求头 {@code X-Ingest-Token}：配置了共享口令就必须带对，未配置时可省略（只限本机开发）
     */
    @PostMapping("/oper")
    public ApiResult<Boolean> oper(@RequestHeader(value = RemoteLogSink.INGEST_TOKEN_HEADER, required = false) String token,
                                   @RequestBody OperLogEntity record) {
        requireToken(token);
        logSink.submit(record);
        return ApiResult.ok(true);
    }

    /**
     * 上报登录审计
     *
     * <p>接收其它服务上报的一条登录结果并落库。</p>
     * <p>
     * 成功与失败都要上报：{@code status} 为 0 的失败记录是判断爆破的唯一现场，缺了这半边等于没审计。
     * {@code username} 按原样存字符串，登录失败时对应的 {@code userId} 就是空。
     *
     * @param token 请求头 {@code X-Ingest-Token}：配置了共享口令就必须带对，未配置时可省略（只限本机开发）
     */
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
