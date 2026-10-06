package org.tiglor.auth.service.impl;

import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Service;
import org.tiglor.api.system.RemoteUserApi;
import org.tiglor.auth.service.ProfileService;
import org.tiglor.common.core.BizException;
import org.tiglor.common.core.ResultCode;

/**
 * 当前登录用户的自助设置。写库在 system-service 侧，这里只做「必须登录」这一道校验。
 */
@Service
public class ProfileServiceImpl implements ProfileService {

    /** Dubbo 的引用注入只能打在非 final 字段上，见 AuthServiceImpl 里同名的说明 */
    @DubboReference
    private RemoteUserApi remoteUserApi;

    @Override
    public void updateTheme(Long userId, String themeKey) {
        if (userId == null) {
            throw new BizException(ResultCode.UNAUTHORIZED, "未登录");
        }
        remoteUserApi.updateTheme(userId, themeKey);
    }
}
