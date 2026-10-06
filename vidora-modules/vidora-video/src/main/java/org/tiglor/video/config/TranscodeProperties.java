package org.tiglor.video.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 转码配置：多清晰度阶梯、硬件加速、HLS 切片、异步线程池。
 * <p>
 * 设计参考大厂视频处理流水线（Jellyfin 的硬件加速矩阵 + ffmpeg ABR 生产配方）：
 * 上传落对象存储后提交异步转码任务，按 renditions 档位出多清晰度 HLS + 主播放列表，
 * 产物回传对象存储并暴露公共播放地址（CDN 思路）。
 * </p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "transcode")
public class TranscodeProperties {

    /** 是否启用异步转码（关闭则不会自动转码，视频保持“待转码”状态） */
    private boolean enabled = true;

    /** 硬件加速：none（软编）/ cuda（NVENC）/ qsv（Intel QuickSync） */
    private String hwaccel = "none";

    /** HLS 切片格式：fmp4（CMAF，现代默认）/ ts（传统 MPEG-TS） */
    private String segmentType = "fmp4";

    /** 单分片时长（秒），现代自适应流默认值 4 */
    private int hlsTime = 4;

    /** 主播放列表文件名 */
    private String masterName = "master.m3u8";

    /** 转码线程池核心/最大线程数 */
    private int corePoolSize = 2;
    private int maxPoolSize = 4;

    /** 单任务最大重试次数 */
    private int maxRetry = 2;

    /**
     * 判定「处理中」任务已僵死的阈值（分钟）：距上次进度写入超过这个时长就认为执行进程没了。
     * <p>
     * 判据是 update_time —— {@code TranscodeTaskRunner.doTranscode} 每过一个阶段都会
     * updateById 刷进度（5→20→70→90→100），MyBatis-Plus 的 AutoFillHandler 顺带把
     * update_time 写成当前时间，所以它冻结不动就说明没人在推进这个任务。
     * </p>
     * <p>
     * 缺省 30 分钟是刻意给宽的：ffmpeg 那一步（进度 20→70）对长视频本来就可能跑十几分钟，
     * 期间一次库都不写。阈值短了会把健康任务误判成僵死，结果同一个任务被两个进程同时转码，
     * 抢着往同一个 outDir 和对象前缀里写。宁可让真僵死的任务多等一会儿。
     * </p>
     */
    private int staleProcessingMinutes = 30;

    /** 清晰度阶梯（ABR ladder），按分辨率从高到低 */
    private List<Rendition> renditions = defaultRenditions();

    @Data
    public static class Rendition {
        /** 档位名，如 1080p */
        private String name;
        private int width;
        private int height;
        /** 目标视频码率（kbps） */
        private int videoBitrate;
        /** VBV 码率上限（kbps），约 1.1×videoBitrate */
        private int maxrate;
        /** VBV 缓冲（kbps），约 2×maxrate */
        private int bufsize;
        /** 音频码率（kbps） */
        private int audioBitrate;
    }

    private static List<Rendition> defaultRenditions() {
        List<Rendition> list = new ArrayList<>();
        list.add(r("1080p", 1920, 1080, 4500, 5500, 11000, 192));
        list.add(r("720p", 1280, 720, 2500, 3200, 6400, 128));
        list.add(r("480p", 854, 480, 1200, 1500, 3000, 128));
        list.add(r("360p", 640, 360, 800, 1000, 2000, 96));
        return list;
    }

    private static Rendition r(String name, int w, int h, int vb, int mr, int bs, int ab) {
        Rendition x = new Rendition();
        x.setName(name);
        x.setWidth(w);
        x.setHeight(h);
        x.setVideoBitrate(vb);
        x.setMaxrate(mr);
        x.setBufsize(bs);
        x.setAudioBitrate(ab);
        return x;
    }
}
