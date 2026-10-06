package org.tiglor.interact.service;

import org.tiglor.interact.dto.VideoTotals;

/**
 * 视频计数的按天累加与汇总。
 * <p>
 * 计数不直接写在 video_info 上，而是按 (video_id, stat_date) 分散到 {@code interact_play_count}：
 * 热门视频每次点击都要 +1，全部压在主表同一行上会让那一行变成写热点（行锁排队）。
 * 按天拆行之后，同一视频的并发写最多争抢「今天」这一行，历史行永远不再被更新。
 * </p>
 * <p>
 * 代价是总数要 SUM，所以读路径挂了 60s 缓存；计数本来就是准实时的，播放数晚一分钟无人在意。
 * video_info 上的冗余计数字段需要由跨服务事件回写，见 .code/ARCHITECTURE.md 的互动服务一节。
 * </p>
 */
public interface PlayCountService {

    /**
     * 上报一次播放，返回是否真的计入了。
     * <p>
     * 同一用户对同一视频在去重窗口内的重复上报只算一次——不设窗口的话，
     * 一个循环调接口的脚本就能把播放数刷到任意值，而播放数直接进热度排序。
     * </p>
     */
    boolean reportPlay(Long videoId, Long userId);

    /**
     * 累加当天计数，delta 可为负（取消点赞、删除评论）。
     * 行不存在则创建，存在则在原值上加，整个过程是一条 SQL，并发安全。
     */
    void accumulate(Long videoId, long playDelta, long likeDelta, long commentDelta, long shareDelta);

    /** 跨天汇总的累计计数，带缓存 */
    VideoTotals totals(Long videoId);
}
