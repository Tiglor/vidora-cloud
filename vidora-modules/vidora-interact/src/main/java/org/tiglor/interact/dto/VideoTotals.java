package org.tiglor.interact.dto;

import lombok.Data;

/** 某视频跨天汇总后的累计计数，由 {@code interact_play_count} 求和得出。 */
@Data
public class VideoTotals {

    /** 回显查询用的视频 ID：SQL 只聚合计数，这一列由服务端拿入参填上 */
    private Long videoId;

    /**
     * 累计播放数，含匿名播放——那部分没有稳定身份可去重，本来就有水分。
     * <p>整对象带 60 秒缓存，所以它是准实时值而不是瞬时值。</p>
     */
    private Long playCount;

    /** 当前净点赞数（取消会扣回去），不是「被点过多少次」；查不到日行时为 0 而非 null */
    private Long likeCount;

    /** 当前净评论数，删评论会扣回去；与评论表实时 COUNT 可能差出一分钟的缓存延迟 */
    private Long commentCount;

    /** 累计分享次数。分享不可取消，所以这一项只增不减 */
    private Long shareCount;
}
