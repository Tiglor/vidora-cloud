package org.tiglor.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.tiglor.common.log.entity.LoginLogEntity;
import org.tiglor.common.log.mapper.LoginLogMapper;
import org.tiglor.system.service.LoginLogService;

import java.time.LocalDateTime;

@Service
public class LoginLogServiceImpl extends ServiceImpl<LoginLogMapper, LoginLogEntity> implements LoginLogService {

    @Override
    public Page<LoginLogEntity> pageLogs(long current, long size, String username, Integer status,
                                        String clientKey, LocalDateTime beginTime, LocalDateTime endTime) {
        return page(new Page<>(current, size), new LambdaQueryWrapper<LoginLogEntity>()
                .like(StringUtils.hasText(username), LoginLogEntity::getUsername, username)
                .eq(status != null, LoginLogEntity::getStatus, status)
                .eq(StringUtils.hasText(clientKey), LoginLogEntity::getClientKey, clientKey)
                .ge(beginTime != null, LoginLogEntity::getCreateTime, beginTime)
                .le(endTime != null, LoginLogEntity::getCreateTime, endTime)
                .orderByDesc(LoginLogEntity::getId));
    }
}
