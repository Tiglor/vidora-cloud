package org.tiglor.system.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.IService;
import org.tiglor.common.log.entity.LoginLogEntity;

import java.time.LocalDateTime;

public interface LoginLogService extends IService<LoginLogEntity> {

    /** 登录日志分页：账号模糊匹配，结果 / 发起端精确匹配，时间倒序 */
    Page<LoginLogEntity> pageLogs(long current, long size, String username, Integer status,
                                  String clientKey, LocalDateTime beginTime, LocalDateTime endTime);
}
