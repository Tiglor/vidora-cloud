package org.tiglor.common.log.sink;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tiglor.common.log.entity.LoginLogEntity;
import org.tiglor.common.log.entity.OperLogEntity;

/**
 * 审计落文件：一行一条 JSON 写进 {@code logs/<服务名>/audit.log}。
 * <p>
 * 这是兜底那条路，也是唯一那条一定成功的路。上报 system-service 会失败（服务没起、
 * 网络抖动、库挂了），审计记录却不能因此消失 —— 合规要的是「发生过什么」，
 * 文件在，事就在。logger 名 AUDIT 与 logback-base.xml 里的 additivity=false 配对，
 * 所以这些行不会重复混进 app.log。
 */
@Slf4j
public class FileLogSink implements LogSink {

    private static final Logger AUDIT = LoggerFactory.getLogger("AUDIT");

    @Override
    public void submit(OperLogEntity record) {
        AUDIT.info(AuditJson.write(new Envelope("oper", record)));
    }

    @Override
    public void submit(LoginLogEntity record) {
        AUDIT.info(AuditJson.write(new Envelope("login", record)));
    }

    /** 带类型的信封，方便后续被采集器按 kind 分流 */
    private record Envelope<T>(String kind, T data) {
    }
}
