package org.tiglor.interact.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.tiglor.common.core.BizException;
import org.tiglor.common.core.ResultCode;
import org.tiglor.interact.dto.ActionCounts;
import org.tiglor.interact.dto.ActionTypeCount;
import org.tiglor.interact.entity.InteractAction;
import org.tiglor.interact.enums.ActionType;
import org.tiglor.interact.enums.TargetType;
import org.tiglor.interact.mapper.InteractActionMapper;
import org.tiglor.interact.service.CommentService;
import org.tiglor.interact.service.InteractActionService;
import org.tiglor.interact.service.PlayCountService;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class InteractActionServiceImpl extends ServiceImpl<InteractActionMapper, InteractAction>
        implements InteractActionService {

    private static final int STATUS_CANCELLED = 0;
    private static final int STATUS_ACTIVE = 1;

    /** 分页上限：不设的话一个 size=1000000 的请求就能把整张表拉出来 */
    private static final long MAX_PAGE_SIZE = 100L;

    private final CommentService commentService;
    private final PlayCountService playCountService;

    @Override
    public ActionCounts setActive(TargetType targetType, Long targetId, ActionType actionType,
                                  boolean active, Long userId) {
        requireUserId(userId);
        if (targetId == null || targetId <= 0) {
            throw new BizException(ResultCode.VALIDATE_FAILED, "targetId 非法");
        }
        // 分享是「发生过的事」，取消分享没有意义；uk 也决定了一个用户对一个对象只留一行
        if (actionType == ActionType.SHARE && !active) {
            throw new BizException(ResultCode.VALIDATE_FAILED, "分享不支持取消");
        }
        int desired = active ? STATUS_ACTIVE : STATUS_CANCELLED;

        // 条件翻转：把「状态确实需要变」写进 WHERE，重复点击不会产生无意义的 UPDATE，
        // 也避免读改写——两个并发请求同时读到旧值再各自写回会丢掉一次变更
        boolean changed = lambdaUpdate()
                .set(InteractAction::getStatus, desired)
                .eq(InteractAction::getUserId, userId)
                .eq(InteractAction::getTargetType, targetType.getCode())
                .eq(InteractAction::getTargetId, targetId)
                .eq(InteractAction::getActionType, actionType.getCode())
                .ne(InteractAction::getStatus, desired)
                .update();
        if (!changed && active && countAction(userId, targetType, targetId, actionType) == 0) {
            changed = insertAction(userId, targetType, targetId, actionType);
        }
        if (changed) {
            applyDerivedCounters(targetType, targetId, actionType, active);
        }
        return counts(targetType, targetId, userId);
    }

    /**
     * 同步各处的冗余计数列。
     * <p>
     * 只在状态「真的翻转」时调用：重复的「点赞」请求 changed 为 false，
     * 若也来加一次，连点几下就能把 like_count 抬到比真实点赞数高，而且再也回不去。
     * </p>
     * <p>
     * 收藏不进任何计数列——它只影响「我的收藏」列表，没有对外展示的总数。
     * </p>
     */
    private void applyDerivedCounters(TargetType targetType, Long targetId, ActionType actionType, boolean active) {
        if (actionType == ActionType.FAVORITE) {
            return;
        }
        if (targetType == TargetType.COMMENT) {
            commentService.bumpLikeCount(targetId, active);
            return;
        }
        long likeDelta = actionType == ActionType.LIKE ? (active ? 1 : -1) : 0;
        // 分享不允许取消（上面已拦），所以这里恒为 +1
        long shareDelta = actionType == ActionType.SHARE ? 1 : 0;
        playCountService.accumulate(targetId, 0, likeDelta, 0, shareDelta);
    }

    @Override
    public ActionCounts counts(TargetType targetType, Long targetId, Long userId) {
        Map<Integer, Long> byType = baseMapper.countByTarget(targetType.getCode(), targetId).stream()
                .collect(Collectors.toMap(ActionTypeCount::getActionType, ActionTypeCount::getTotal));

        ActionCounts result = new ActionCounts();
        result.setTargetType(targetType.getCode());
        result.setTargetId(targetId);
        result.setLikeCount(byType.getOrDefault(ActionType.LIKE.getCode(), 0L));
        result.setFavoriteCount(byType.getOrDefault(ActionType.FAVORITE.getCode(), 0L));
        result.setShareCount(byType.getOrDefault(ActionType.SHARE.getCode(), 0L));

        if (userId != null) {
            Set<Integer> mine = lambdaQuery()
                    .select(InteractAction::getActionType)
                    .eq(InteractAction::getUserId, userId)
                    .eq(InteractAction::getTargetType, targetType.getCode())
                    .eq(InteractAction::getTargetId, targetId)
                    .eq(InteractAction::getStatus, STATUS_ACTIVE)
                    .in(InteractAction::getActionType, ActionType.LIKE.getCode(), ActionType.FAVORITE.getCode())
                    .list().stream()
                    .map(InteractAction::getActionType)
                    .collect(Collectors.toSet());
            // 未登录时保持 null：前端要区分「没登录，点了先跳登录」和「登录了但没点过」
            result.setLiked(mine.contains(ActionType.LIKE.getCode()));
            result.setFavorited(mine.contains(ActionType.FAVORITE.getCode()));
        }
        return result;
    }

    @Override
    public Page<InteractAction> myFavorites(Long userId, long current, long size) {
        requireUserId(userId);
        return lambdaQuery()
                .eq(InteractAction::getUserId, userId)
                .eq(InteractAction::getActionType, ActionType.FAVORITE.getCode())
                .eq(InteractAction::getStatus, STATUS_ACTIVE)
                .orderByDesc(InteractAction::getUpdateTime)
                .page(new Page<>(Math.max(current, 1), Math.min(Math.max(size, 1), MAX_PAGE_SIZE)));
    }

    /**
     * 插入一条新的动作记录，返回是否真的插进去了。
     * <p>
     * 不校验 target 是否存在：video_info 归 video-service 写，跨服务校验会给最热的写路径加一次远程调用。
     * 编造的 targetId 只会留下一行没人读的死数据——计数总是和对象一起被查出来的。
     * </p>
     */
    private boolean insertAction(Long userId, TargetType targetType, Long targetId, ActionType actionType) {
        InteractAction action = new InteractAction();
        action.setUserId(userId);
        action.setTargetType(targetType.getCode());
        action.setTargetId(targetId);
        action.setActionType(actionType.getCode());
        action.setStatus(STATUS_ACTIVE);
        try {
            save(action);
            return true;
        } catch (DuplicateKeyException e) {
            // 双击 / 两个标签页同时点：唯一键挡下第二行，此时库里已有一条 status=1 的记录，
            // 和本次请求想要的结果一致，按成功处理；但计数不能再加一次，抢到锁的那个请求已经加过了
            log.debug("并发插入互动记录被唯一键拦截：userId={}, target={}:{}, action={}",
                    userId, targetType.getCode(), targetId, actionType.getCode());
            return false;
        }
    }

    private long countAction(Long userId, TargetType targetType, Long targetId, ActionType actionType) {
        Long count = lambdaQuery()
                .eq(InteractAction::getUserId, userId)
                .eq(InteractAction::getTargetType, targetType.getCode())
                .eq(InteractAction::getTargetId, targetId)
                .eq(InteractAction::getActionType, actionType.getCode())
                .count();
        return count == null ? 0L : count;
    }

    private static void requireUserId(Long userId) {
        if (userId == null) {
            throw new BizException(ResultCode.UNAUTHORIZED, "未登录");
        }
    }
}
