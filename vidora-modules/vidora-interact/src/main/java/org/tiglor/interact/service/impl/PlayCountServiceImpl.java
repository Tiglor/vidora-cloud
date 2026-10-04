package org.tiglor.interact.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.tiglor.common.redis.CacheNames;
import org.tiglor.interact.dto.VideoTotals;
import org.tiglor.interact.mapper.PlayCountMapper;
import org.tiglor.interact.service.PlayCountService;

import java.time.Duration;
import java.time.LocalDate;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlayCountServiceImpl implements PlayCountService {

    /**
     * 播放去重窗口。取 5 分钟是「一次观看」的经验值：
     * 短了挡不住脚本连点，长了会把正常的重播也算成同一次。
     */
    private static final Duration PLAY_DEDUPE_WINDOW = Duration.ofMinutes(5);

    /** 与 RedisCacheConfig 的前缀约定保持一致，便于按前缀排查 */
    private static final String PLAY_DEDUPE_PREFIX = "vidora:interact:play-dedupe:";

    private final PlayCountMapper playCountMapper;
    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    public boolean reportPlay(Long videoId, Long userId) {
        if (videoId == null || videoId <= 0) {
            return false;
        }
        // 未登录的播放不去重（没有稳定的身份可依据），照实计数：
        // 宁可让匿名播放有水分，也不能因为拿不到 userId 就把这部分播放全丢掉
        if (userId != null && !markFirstPlayInWindow(videoId, userId)) {
            return false;
        }
        accumulate(videoId, 1, 0, 0, 0);
        return true;
    }

    @Override
    public void accumulate(Long videoId, long playDelta, long likeDelta, long commentDelta, long shareDelta) {
        if (videoId == null || videoId <= 0) {
            return;
        }
        if (playDelta == 0 && likeDelta == 0 && commentDelta == 0 && shareDelta == 0) {
            return;
        }
        // 一律记到「今天」这一行。跨零点的那次请求会落到新的一天，
        // 边界上差一次计数没有影响，换来的是不需要为「算哪一天」做任何协商
        playCountMapper.accumulate(videoId, LocalDate.now(), playDelta, likeDelta, commentDelta, shareDelta);
    }

    /**
     * 靠 TTL 过期而不是写时失效：播放上报是高频写，每次都 evict 等于把缓存废掉，
     * 热门视频会退化成「每播放一次就 SUM 一次全部日行」。计数晚一分钟可见是可接受的。
     */
    @Override
    @Cacheable(cacheNames = CacheNames.INTERACT_VIDEO_TOTALS, key = "#videoId")
    public VideoTotals totals(Long videoId) {
        VideoTotals totals = playCountMapper.sumByVideo(videoId);
        if (totals == null) {
            totals = new VideoTotals();
            totals.setPlayCount(0L);
            totals.setLikeCount(0L);
            totals.setCommentCount(0L);
            totals.setShareCount(0L);
        }
        totals.setVideoId(videoId);
        return totals;
    }

    /**
     * SETNX + TTL 占一个去重位，返回是否是窗口内的首次播放。
     * <p>
     * Redis 不可用时选择**放行**：播放数多算一次的代价，远小于「Redis 挂了导致播放上报接口报错」。
     * 缓存层的降级见 RedisCacheConfig#errorHandler，这里是同一套取舍。
     * </p>
     */
    private boolean markFirstPlayInWindow(Long videoId, Long userId) {
        String key = PLAY_DEDUPE_PREFIX + videoId + ":" + userId;
        try {
            Boolean first = redisTemplate.opsForValue().setIfAbsent(key, "1", PLAY_DEDUPE_WINDOW);
            return Boolean.TRUE.equals(first);
        } catch (Exception e) {
            log.warn("播放去重不可用，本次照常计数：videoId={}, userId={}", videoId, userId, e);
            return true;
        }
    }
}
