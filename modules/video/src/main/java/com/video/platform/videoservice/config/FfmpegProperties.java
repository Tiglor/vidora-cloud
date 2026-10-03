package com.video.platform.videoservice.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * FFmpeg 配置：调用本机 ffmpeg / ffprobe 命令行（需预先安装）
 */
@Data
@Component
@ConfigurationProperties(prefix = "ffmpeg")
public class FfmpegProperties {

    /** ffmpeg 可执行文件路径或命令名 */
    private String ffmpegPath = "ffmpeg";
    /** ffprobe 可执行文件路径或命令名 */
    private String ffprobePath = "ffprobe";

    /** HLS 单分片时长（秒） */
    private int hlsTime = 10;
    /** 转码临时目录 */
    private String workDir = "./transcode";
}
