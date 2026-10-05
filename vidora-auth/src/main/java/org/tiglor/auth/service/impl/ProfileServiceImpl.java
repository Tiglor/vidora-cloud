package org.tiglor.auth.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.tiglor.auth.service.ProfileService;
import org.tiglor.common.core.BizException;
import org.tiglor.common.core.ResultCode;
import org.tiglor.common.user.entity.User;
import org.tiglor.common.user.mapper.UserMapper;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ProfileServiceImpl implements ProfileService {

    private final UserMapper userMapper;

    @Override
    public void updateTheme(Long userId, String themeKey) {
        if (userId == null) {
            throw new BizException(ResultCode.UNAUTHORIZED, "未登录");
        }
        // 只更新这一列，而不是 updateById(entity)：后者会把整个实体带上去，
        // 哪天 User 加了字段，这里就成了「用户能改自己任意资料」的越权口子
        userMapper.update(null, Wrappers.<User>lambdaUpdate()
                .eq(User::getId, userId)
                .set(User::getThemeKey, themeKey));
    }
}
