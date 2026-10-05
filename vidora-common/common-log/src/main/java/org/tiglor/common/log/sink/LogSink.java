package org.tiglor.common.log.sink;

import org.tiglor.common.log.entity.LoginLogEntity;
import org.tiglor.common.log.entity.OperLogEntity;

/**
 * 审计日志的去向。
 * <p>
 * 只定义「交出去」这一件事，不关心它是进文件还是进库：切面拿不到 IO，
 * 就不会出现「上报超时把用户的删除请求一起拖失败」这种事故。
 */
public interface LogSink {

    void submit(OperLogEntity log);

    void submit(LoginLogEntity log);
}
