package org.tiglor.system.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import org.tiglor.common.user.entity.User;
import org.tiglor.common.user.mapper.UserMapper;
import org.tiglor.system.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    /** 兜住外部传入的 size：一个不带上限的 size 会把整张 sys_user 拉进内存 */
    private static final long MAX_PAGE_SIZE = 100L;

    @Override
    public Page<User> pageUsers(long current, long size, String phone, String nickname, Integer status) {
        String phoneLike = trimToNull(phone);
        String nicknameLike = trimToNull(nickname);
        // 空串必须归一成 null：LIKE '%%' 在功能上等于不加条件，但会白白丢掉 idx_status 的等值前缀
        return lambdaQuery()
                .like(phoneLike != null, User::getPhone, phoneLike)
                .like(nicknameLike != null, User::getNickname, nicknameLike)
                .eq(status != null, User::getStatus, status)
                // id 兜底排序键：同一秒注册的用户只按 create_time 排，翻页时顺序会抖，同一行可能重复出现或漏掉
                .orderByDesc(User::getCreateTime)
                .orderByDesc(User::getId)
                .page(new Page<>(Math.max(current, 1), clampSize(size)));
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static long clampSize(long size) {
        return Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
    }
}
