package com.video.platform.videoservice.entity;

import com.video.platform.common.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 视频转码任务：与上传/播放解耦，状态机 PENDING→PROCESSING→SUCCESS/FAILED，支持重试。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("video_transcode_task")
public class TranscodeTask extends BaseEntity {

    /** 关联 video_info.id */
    private Long videoId;
    /** 视频唯一 key */
    private String videoKey;

    /** 状态：0-待处理 1-处理中 2-成功 3-失败 */
    private Integer status;
    /** 进度 0-100 */
    private Integer progress;

    /** 源片存储对象名 */
    private String sourcePath;
    /** 主播放列表 objectName（master.m3u8） */
    private String hlsPath;
    /** 已生成档位（JSON 数组，如 ["1080p","720p"]） */
    private String renditions;

    /** 失败原因 */
    private String errorMsg;
    /** 已重试次数 */
    private Integer retryCount;

    /** 完成时间 */
    private LocalDateTime finishedAt;
}
