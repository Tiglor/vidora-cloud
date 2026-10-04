package org.tiglor.interact.dto;

import lombok.Data;

/** 某视频跨天汇总后的累计计数，由 {@code interact_play_count} 求和得出。 */
@Data
public class VideoTotals {

    private Long videoId;
    private Long playCount;
    private Long likeCount;
    private Long commentCount;
    private Long shareCount;
}
