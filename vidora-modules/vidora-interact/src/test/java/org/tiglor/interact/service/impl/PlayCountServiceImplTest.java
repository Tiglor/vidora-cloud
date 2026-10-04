package org.tiglor.interact.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.tiglor.interact.dto.VideoTotals;
import org.tiglor.interact.mapper.PlayCountMapper;

import java.time.Duration;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 播放计数的去重窗口与降级策略。
 * <p>
 * 这两个 ServiceImpl 里只有它不继承 MyBatis-Plus 的 ServiceImpl（直接持有 Mapper），
 * 所以不需要 {@code MpTestSupport} 那套反射装配。
 * </p>
 */
class PlayCountServiceImplTest {

    private static final long VIDEO_ID = 7L;
    private static final long USER_ID = 42L;
    private static final String DEDUPE_KEY = "vidora:interact:play-dedupe:7:42";

    PlayCountMapper mapper;
    RedisTemplate<String, Object> redisTemplate;
    ValueOperations<String, Object> valueOps;
    PlayCountServiceImpl service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        mapper = mock(PlayCountMapper.class);
        redisTemplate = mock(RedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        service = new PlayCountServiceImpl(mapper, redisTemplate);
    }

    @Test
    @DisplayName("窗口内首次播放被计数，去重键带上 5 分钟 TTL")
    void firstPlayInWindowIsCounted() {
        when(valueOps.setIfAbsent(eq(DEDUPE_KEY), any(), any(Duration.class))).thenReturn(true);

        assertThat(service.reportPlay(VIDEO_ID, USER_ID)).isTrue();

        verify(mapper).accumulate(VIDEO_ID, LocalDate.now(), 1L, 0L, 0L, 0L);
        verify(valueOps).setIfAbsent(DEDUPE_KEY, "1", Duration.ofMinutes(5));
    }

    @Test
    @DisplayName("窗口内重复播放不再计数")
    void secondPlayInWindowIsDropped() {
        when(valueOps.setIfAbsent(anyString(), any(), any(Duration.class))).thenReturn(false);

        assertThat(service.reportPlay(VIDEO_ID, USER_ID)).isFalse();

        verify(mapper, never()).accumulate(anyLong(), any(), anyLong(), anyLong(), anyLong(), anyLong());
    }

    @Test
    @DisplayName("匿名播放不去重：拿不到 userId 也要照实计数")
    void anonymousPlaysAreNeverDeduped() {
        assertThat(service.reportPlay(VIDEO_ID, null)).isTrue();

        verifyNoInteractions(valueOps);
        verify(mapper).accumulate(VIDEO_ID, LocalDate.now(), 1L, 0L, 0L, 0L);
    }

    @Test
    @DisplayName("Redis 挂掉时放行计数，而不是让上报接口报错")
    void redisOutageStillCounts() {
        when(redisTemplate.opsForValue()).thenThrow(new IllegalStateException("connection refused"));

        assertThat(service.reportPlay(VIDEO_ID, USER_ID)).isTrue();

        verify(mapper).accumulate(VIDEO_ID, LocalDate.now(), 1L, 0L, 0L, 0L);
    }

    @Test
    @DisplayName("非法 videoId 直接忽略，连 Redis 都不碰")
    void invalidVideoIdIsIgnored() {
        assertThat(service.reportPlay(0L, USER_ID)).isFalse();

        verifyNoInteractions(redisTemplate);
        verifyNoInteractions(mapper);
    }

    @Test
    @DisplayName("增量全为 0 时 accumulate 是空操作")
    void accumulateWithAllZeroDeltasIsANoOp() {
        service.accumulate(VIDEO_ID, 0, 0, 0, 0);

        verifyNoInteractions(mapper);
    }

    @Test
    @DisplayName("accumulate 一律落到当天那一行")
    void accumulateWritesToTodaysRow() {
        service.accumulate(VIDEO_ID, 0, 1, 0, 0);

        verify(mapper).accumulate(VIDEO_ID, LocalDate.now(), 0L, 1L, 0L, 0L);
    }

    @Test
    @DisplayName("一行日统计都没有时 totals 返回全 0 而不是 null")
    void totalsFallsBackToZerosWhenThereAreNoRows() {
        when(mapper.sumByVideo(VIDEO_ID)).thenReturn(null);

        VideoTotals totals = service.totals(VIDEO_ID);

        assertThat(totals).isNotNull();
        assertThat(totals.getVideoId()).isEqualTo(VIDEO_ID);
        assertThat(totals.getPlayCount()).isZero();
        assertThat(totals.getLikeCount()).isZero();
        assertThat(totals.getCommentCount()).isZero();
        assertThat(totals.getShareCount()).isZero();
    }

    @Test
    @DisplayName("totals 把跨天的日行求和结果透传出去")
    void totalsPassesThroughTheSum() {
        VideoTotals summed = new VideoTotals();
        summed.setPlayCount(120L);
        summed.setLikeCount(9L);
        summed.setCommentCount(4L);
        summed.setShareCount(2L);
        when(mapper.sumByVideo(VIDEO_ID)).thenReturn(summed);

        VideoTotals totals = service.totals(VIDEO_ID);

        assertThat(totals.getPlayCount()).isEqualTo(120L);
        assertThat(totals.getLikeCount()).isEqualTo(9L);
        assertThat(totals.getCommentCount()).isEqualTo(4L);
        assertThat(totals.getShareCount()).isEqualTo(2L);
        assertThat(totals.getVideoId()).isEqualTo(VIDEO_ID);
    }
}
