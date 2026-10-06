package org.tiglor.recommend.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.tiglor.common.core.BizException;
import org.tiglor.common.core.ResultCode;
import org.tiglor.recommend.dto.RecommendBatchRequest;
import org.tiglor.recommend.dto.RecommendItemRequest;
import org.tiglor.recommend.entity.RecommendResult;
import org.tiglor.recommend.enums.AlgoType;
import org.tiglor.recommend.enums.RecommendScene;
import org.tiglor.recommend.mapper.RecommendResultMapper;
import org.tiglor.recommend.service.RecommendResultService;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 推荐候选。
 * <p>
 * 每个查询都带 {@code user_id}，只有管理端 {@link #page} 例外（由 {@code recommend:manage} 权限挡住）。
 * 原先的壳控制器 {@code GET /recommends/page} 没有任何过滤条件，任何登录用户都能翻遍全站的推荐候选，
 * 等于把「谁在被推什么」这份数据公开了。
 * </p>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class RecommendResultServiceImpl extends ServiceImpl<RecommendResultMapper, RecommendResult>
        implements RecommendResultService {

    /** 单条 INSERT 语句最多拼多少行，超了会撞 {@code max_allowed_packet} */
    public static final int BATCH_CHUNK = 500;

    private static final long MAX_PAGE_SIZE = 100L;

    /** 获取推荐流的分布式锁等待时间（秒），避免大量请求同时争锁导致雪崩 */
    private static final int LOCK_WAIT_SECONDS = 2;
    /** 锁自动释放时间（秒）——取候选 + 标记曝光正常 10ms 内完成，给 30s 兜底防止死锁 */
    private static final int LOCK_LEASE_SECONDS = 30;

    private final RedissonClient redissonClient;

    @Override
    public List<RecommendResult> feed(Long userId, String scene, int size) {
        // 匿名用户没有个性化推荐，直接返回空列表，由前端降级为热门/最新内容
        if (userId == null) {
            return List.of();
        }
        Long uid = userId;
        String sceneCode = RecommendScene.of(scene).getCode();
        int limit = (int) Math.min(Math.max(size, 1), MAX_FEED_SIZE);

        // 每个用户每个场景一把独立的锁：同一用户并发刷新 feed 时不会重复下发同一屏候选
        String lockKey = "recommend:feed:" + uid + ":" + sceneCode;
        RLock lock = redissonClient.getLock(lockKey);
        boolean locked = false;
        try {
            locked = lock.tryLock(LOCK_WAIT_SECONDS, LOCK_LEASE_SECONDS, TimeUnit.SECONDS);
            if (!locked) {
                // 没抢到锁说明这个用户正在刷新 feed，直接返回空列表让前端静默重试——
                // 不降级返回旧候选（避免曝光计数被置两次），也不阻塞等锁（避免请求堆积）
                log.debug("推荐流锁竞争，返回空列表让前端重试：userId={} scene={}", uid, sceneCode);
                return List.of();
            }

            // searchCount=false：feed 只要这一屏，不需要知道总共有多少条候选
            Page<RecommendResult> page = new Page<>(1, limit, false);
            lambdaQuery()
                    .eq(RecommendResult::getUserId, uid)
                    .eq(RecommendResult::getScene, sceneCode)
                    .eq(RecommendResult::getIsExposed, RecommendResult.FLAG_NO)
                    .orderByDesc(RecommendResult::getScore)
                    // id 是必需的兜底排序键：分数相同的候选没有稳定顺序的话，
                    // 同一屏刷新两次会给出不同的排列
                    .orderByAsc(RecommendResult::getId)
                    .page(page);
            List<RecommendResult> candidates = page.getRecords();
            if (candidates.isEmpty()) {
                return candidates;
            }

            List<Long> ids = candidates.stream().map(RecommendResult::getId).toList();
            // 条件更新（带上 is_exposed = 0）而不是无条件置位：
            // 并发时另一个请求可能已经翻过了，影响行数会小于 ids.size()，
            // 但现在有锁保护后这里应该始终等于 ids.size()，保留条件只是双保险
            int claimed = baseMapper.update(null, Wrappers.<RecommendResult>lambdaUpdate()
                    .set(RecommendResult::getIsExposed, RecommendResult.FLAG_YES)
                    .eq(RecommendResult::getUserId, uid)
                    .in(RecommendResult::getId, ids)
                    .eq(RecommendResult::getIsExposed, RecommendResult.FLAG_NO));
            if (claimed < ids.size()) {
                log.warn("推荐流标记数量小于取出数量（锁保护下不应发生）：userId={} scene={} 取出={} 标记={}",
                        uid, sceneCode, ids.size(), claimed);
            }
            return candidates;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("获取推荐流锁被中断：userId={} scene={}", uid, sceneCode, e);
            return List.of();
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    @Override
    public boolean reportClick(Long userId, long id) {
        Long uid = requireLogin(userId);
        // 带上 user_id 过滤：不带的话猜到一个自增 id 就能替别人记一次点击，CTR 直接失真。
        // 再带上 is_clicked = 0，重复上报返回 false 而不是又更新一遍
        int rows = baseMapper.update(null, Wrappers.<RecommendResult>lambdaUpdate()
                .set(RecommendResult::getIsClicked, RecommendResult.FLAG_YES)
                // 点击蕴含曝光，见接口注释
                .set(RecommendResult::getIsExposed, RecommendResult.FLAG_YES)
                .eq(RecommendResult::getId, id)
                .eq(RecommendResult::getUserId, uid)
                .eq(RecommendResult::getIsClicked, RecommendResult.FLAG_NO));
        return rows > 0;
    }

    @Override
    public Page<RecommendResult> myHistory(Long userId, String scene, long current, long size) {
        Long uid = requireLogin(userId);
        String sceneCode = normalizeScene(scene);
        return lambdaQuery()
                .eq(RecommendResult::getUserId, uid)
                .eq(sceneCode != null, RecommendResult::getScene, sceneCode)
                .eq(RecommendResult::getIsExposed, RecommendResult.FLAG_YES)
                .orderByDesc(RecommendResult::getId)
                .page(new Page<>(Math.max(current, 1), clampSize(size)));
    }

    @Override
    public int batchUpsert(RecommendBatchRequest request) {
        List<RecommendItemRequest> items = request.getItems();
        List<RecommendResult> rows = new ArrayList<>(items.size());
        for (RecommendItemRequest item : items) {
            RecommendResult row = new RecommendResult();
            row.setUserId(item.getUserId());
            row.setVideoId(item.getVideoId());
            // 存规范值而不是原始字符串："HOME" 和 "home" 在 _ci 唯一键下是同一行，
            // 但库里会留下两种写法，事后按 scene 统计还得再做一次大小写归一
            row.setScene(RecommendScene.of(item.getScene()).getCode());
            row.setAlgoType(AlgoType.of(item.getAlgoType()).getCode());
            row.setScore(item.getScore() == null ? BigDecimal.ZERO : item.getScore());
            rows.add(row);
        }
        // 不包事务：upsert 幂等，中途失败算法任务重跑整批即可；
        // 把几十条 INSERT 圈在一个事务里只会长时间占着连接
        for (int from = 0; from < rows.size(); from += BATCH_CHUNK) {
            baseMapper.batchUpsert(rows.subList(from, Math.min(from + BATCH_CHUNK, rows.size())));
        }
        return rows.size();
    }

    @Override
    public Page<RecommendResult> page(long current, long size, Long userId, String scene,
                                      Boolean exposed, Boolean clicked) {
        String sceneCode = normalizeScene(scene);
        return lambdaQuery()
                .eq(userId != null, RecommendResult::getUserId, userId)
                .eq(sceneCode != null, RecommendResult::getScene, sceneCode)
                .eq(exposed != null, RecommendResult::getIsExposed, flag(exposed))
                .eq(clicked != null, RecommendResult::getIsClicked, flag(clicked))
                .orderByDesc(RecommendResult::getId)
                .page(new Page<>(Math.max(current, 1), clampSize(size)));
    }

    @Override
    public int pruneStale(int days) {
        if (days < 1) {
            throw new BizException(ResultCode.VALIDATE_FAILED, "days 至少是 1，不允许一次清空整张表");
        }
        // 命中 idx_create_time
        return baseMapper.delete(Wrappers.<RecommendResult>lambdaQuery()
                .lt(RecommendResult::getCreateTime, LocalDateTime.now().minusDays(days)));
    }

    private static Long requireLogin(Long userId) {
        if (userId == null) {
            throw new BizException(ResultCode.UNAUTHORIZED, "未登录");
        }
        return userId;
    }

    /** 空值表示「不按场景过滤」，非法值仍然要报错 */
    private static String normalizeScene(String scene) {
        return scene == null || scene.isBlank() ? null : RecommendScene.of(scene).getCode();
    }

    private static Integer flag(Boolean value) {
        return value == null ? null : (value ? RecommendResult.FLAG_YES : RecommendResult.FLAG_NO);
    }

    private static long clampSize(long size) {
        return Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
    }
}
