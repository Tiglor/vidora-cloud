package org.tiglor.system.sink;

import lombok.RequiredArgsConstructor;
import org.tiglor.common.log.entity.LoginLogEntity;
import org.tiglor.common.log.entity.OperLogEntity;
import org.tiglor.common.log.sink.FileLogSink;
import org.tiglor.common.log.sink.LogSink;
import org.tiglor.system.service.LoginLogService;
import org.tiglor.system.service.OperLogService;
import org.springframework.stereotype.Component;

/**
 * system-service 自己的审计去向：直接落库，外加一份本地 audit.log。
 * <p>
 * common-log 默认给的是「异步 HTTP 上报 system-service」，但本服务本身就是那个接收方，
 * 再绕一圈 HTTP 调自己既没必要也要多一次序列化。这里定义了 LogSink Bean，
 * 自动配置里的 {@code @ConditionalOnMissingBean} 就会让位，上报通道自动关掉。
 * <p>
 * 仍然先写文件再写库：库写不进去（表还没建、连接池打满）时审计不能凭空消失。
 */
@Component
@RequiredArgsConstructor
public class DatabaseLogSink implements LogSink {

    private final OperLogService operLogService;
    private final LoginLogService loginLogService;
    private final FileLogSink fileLogSink = new FileLogSink();

    @Override
    public void submit(OperLogEntity record) {
        fileLogSink.submit(record);
        operLogService.save(record);
    }

    @Override
    public void submit(LoginLogEntity record) {
        fileLogSink.submit(record);
        loginLogService.save(record);
    }
}
