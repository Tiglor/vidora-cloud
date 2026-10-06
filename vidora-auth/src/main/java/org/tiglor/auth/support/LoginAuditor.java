package org.tiglor.auth.support;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.tiglor.auth.dto.UserLoginDTO;
import org.tiglor.auth.vo.LoginVO;
import org.tiglor.common.core.support.TraceIds;
import org.tiglor.common.log.entity.LoginLogEntity;
import org.tiglor.common.log.sink.LogSink;
import org.tiglor.common.log.web.ClientAddress;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 登录审计：成功和失败都记一条 sys_login_log。
 * <p>
 * 失败必须记：爆破、撞库、员工账号被人试密码，现场只在失败记录里。
 * 只记成功的那套登录日志，出事时查不到任何东西。
 * <p>
 * 刻意不记密码字段（连哈希都不记），只记账号、来源端、IP、UA 和失败原因。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LoginAuditor {

    /** user_agent 列宽，与 SQL/vidora_cloud.sql 审计节的 sys_login_log 一致；超长直接截断而不是拒录 */
    private static final int MAX_USER_AGENT = 500;
    private static final int MAX_MSG = 200;

    private final LogSink logSink;

    public void success(UserLoginDTO dto, LoginVO login, HttpServletRequest request) {
        LoginLogEntity record = base(dto, request);
        record.setUserId(login.getUserId());
        record.setClientKey(login.getClientKey());
        record.setStatus(1);
        submit(record);
    }

    /**
     * 登录失败。此时客户端可能根本没解析成功，所以 {@code client_key} 允许为空，
     * 但账号、IP、UA 一定记下来——这三个才是判断「谁在试」的关键。
     */
    public void failure(UserLoginDTO dto, String reason, HttpServletRequest request) {
        LoginLogEntity record = base(dto, request);
        record.setStatus(0);
        record.setMsg(truncate(reason, MAX_MSG));
        submit(record);
    }

    private LoginLogEntity base(UserLoginDTO dto, HttpServletRequest request) {
        LoginLogEntity record = new LoginLogEntity();
        record.setTraceId(MDC.get(TraceIds.MDC_KEY));
        record.setUsername(dto.getPhone());
        record.setIp(ClientAddress.ip(request));
        record.setUserAgent(truncate(ClientAddress.userAgent(request), MAX_USER_AGENT));
        return record;
    }

    private void submit(LoginLogEntity record) {
        try {
            logSink.submit(record);
        } catch (Exception e) {
            // 审计写不进去不能把登录带崩：用户还在等 token
            log.warn("登录审计上报失败 phone={}：{}", record.getUsername(), e.toString());
        }
    }

    private static String truncate(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max);
    }
}
