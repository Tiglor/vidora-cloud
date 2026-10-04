package org.tiglor.interact.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.tiglor.interact.dto.VideoTotals;
import org.tiglor.interact.entity.PlayCount;

import java.time.LocalDate;

@Mapper
public interface PlayCountMapper extends BaseMapper<PlayCount> {

    /**
     * 按天累加计数：行不存在就插入，存在就在原值上加，并且不会降到 0 以下。
     * <p>
     * 必须一条 SQL 搞定，不能「先查再决定插入还是更新」——播放上报是并发的，
     * 读改写会在 uk_video_date 上撞出一堆 DuplicateKeyException，或者两个线程互相覆盖增量。
     * create_time / update_time 交给列上的 DEFAULT / ON UPDATE 维护。
     * </p>
     * <p>
     * GREATEST(..., 0) 是给负增量兜底：取消点赞、删评论都是 -1，事件重放或人工改库
     * 都可能多减一次，计数变成负数在前端是脏数据，回溯起来又很费劲。
     * </p>
     */
    @Insert("""
            INSERT INTO interact_play_count
                (video_id, play_count, like_count, comment_count, share_count, stat_date)
            VALUES (#{videoId}, #{playCount}, #{likeCount}, #{commentCount}, #{shareCount}, #{statDate})
            ON DUPLICATE KEY UPDATE
                play_count = GREATEST(play_count + VALUES(play_count), 0),
                like_count = GREATEST(like_count + VALUES(like_count), 0),
                comment_count = GREATEST(comment_count + VALUES(comment_count), 0),
                share_count = GREATEST(share_count + VALUES(share_count), 0)
            """)
    int accumulate(@Param("videoId") Long videoId,
                   @Param("statDate") LocalDate statDate,
                   @Param("playCount") long playCount,
                   @Param("likeCount") long likeCount,
                   @Param("commentCount") long commentCount,
                   @Param("shareCount") long shareCount);

    /** 跨天汇总某视频的累计计数 */
    @Select("""
            SELECT COALESCE(SUM(play_count), 0)    AS playCount,
                   COALESCE(SUM(like_count), 0)    AS likeCount,
                   COALESCE(SUM(comment_count), 0) AS commentCount,
                   COALESCE(SUM(share_count), 0)   AS shareCount
            FROM interact_play_count
            WHERE video_id = #{videoId}
            """)
    VideoTotals sumByVideo(@Param("videoId") Long videoId);
}
