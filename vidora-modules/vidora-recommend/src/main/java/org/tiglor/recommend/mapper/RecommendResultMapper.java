package org.tiglor.recommend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.tiglor.recommend.entity.RecommendResult;

import java.util.List;

@Mapper
public interface RecommendResultMapper extends BaseMapper<RecommendResult> {

    /**
     * 离线算法任务批量写入候选。
     * <p>
     * 撞 {@code uk_user_video_scene} 时只刷新 {@code score} 和 {@code algo_type}，
     * <b>不</b>碰 {@code is_exposed} / {@code is_clicked}。这两个是一次性事实：
     * 重置曝光位会让已经给用户看过的视频重新回到 feed 里，重置点击位会直接毁掉 CTR 统计。
     * </p>
     * <p>
     * 插入时两列都不出现在列清单里，走 DDL 的 {@code DEFAULT 0}。
     * </p>
     * <p>
     * <b>返回值不是行数</b>：MySQL 对 {@code ON DUPLICATE KEY UPDATE} 的约定是
     * 插入返回 1、更新返回 2、命中但值没变返回 0，一批 N 条的返回值在 0 到 2N 之间。
     * 调用方要的是「这批写进去了没有」，不是精确条数。
     * </p>
     * <p>
     * 单条语句的 {@code items} 数量由服务层分批控制（见
     * {@code RecommendResultServiceImpl.BATCH_CHUNK}）：一条 foreach 拼出来的 SQL
     * 长度受 {@code max_allowed_packet} 限制，超了是整批失败而不是少写几条。
     * </p>
     */
    @Insert("""
            <script>
            INSERT INTO recommend_result (user_id, video_id, scene, score, algo_type)
            VALUES
            <foreach collection="items" item="it" separator=",">
                (#{it.userId}, #{it.videoId}, #{it.scene}, #{it.score}, #{it.algoType})
            </foreach>
            ON DUPLICATE KEY UPDATE
                score     = VALUES(score),
                algo_type = VALUES(algo_type)
            </script>
            """)
    int batchUpsert(@Param("items") List<RecommendResult> items);
}
