package org.tiglor.video.service;

import lombok.Data;

/**
 * 媒体探测结果（来自 ffprobe）
 */
@Data
public class MediaInfo {

    /** 时长（秒） */
    private Integer durationSec;
    private Integer width;
    private Integer height;
    private String videoCodec;
    private String audioCodec;
    /** 码率（bps） */
    private Long bitrate;
}
