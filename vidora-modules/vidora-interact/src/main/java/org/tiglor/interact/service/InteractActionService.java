package org.tiglor.interact.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.tiglor.interact.dto.ActionCounts;
import org.tiglor.interact.entity.InteractAction;
import org.tiglor.interact.enums.ActionType;
import org.tiglor.interact.enums.TargetType;

/**
 * 点赞 / 收藏 / 分享。
 * <p>
 * 三种动作共用 {@code interact_action} 一张表，靠 {@code uk_user_target_action} 保证
 * 「一个用户对一个对象的一种动作只有一行」，所以点赞/取消点赞是**翻转 status**而不是增删行——
 * 删行的话历史就没了，而且并发下容易和插入撞唯一键。
 * </p>
 */
public interface InteractActionService {

    /**
     * 把当前用户对某对象的某个动作设为 active / 取消，幂等：重复调用结果一致，不会重复计数。
     *
     * @return 设置后的最新计数与用户状态
     */
    ActionCounts setActive(TargetType targetType, Long targetId, ActionType actionType,
                           boolean active, Long userId);

    /** 查询某对象的计数；userId 为 null（未登录）时 liked / favorited 返回 null 而不是 false */
    ActionCounts counts(TargetType targetType, Long targetId, Long userId);

    /** 我的收藏列表，按收藏时间倒序 */
    Page<InteractAction> myFavorites(Long userId, long current, long size);
}
