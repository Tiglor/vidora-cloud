package org.tiglor.video.service.impl;

import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.tiglor.common.core.BizException;
import org.tiglor.common.core.ResultCode;
import org.tiglor.video.config.FfmpegProperties;
import org.tiglor.video.config.TranscodeProperties;
import org.tiglor.video.entity.TranscodeTask;
import org.tiglor.video.entity.VideoInfo;
import org.tiglor.video.service.StorageService;
import org.tiglor.video.service.TranscodeTaskService;
import org.tiglor.video.service.VideoInfoService;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;

/**
 * 转码任务的执行单元：同步跑一次 ffmpeg 出多清晰度 HLS，回传对象存储并回写视频。
 * <p>
 * 刻意做成<b>同步且不带重试</b>——重试策略属于投递方：走 MQ 时由 broker 重投（进程重启也不丢），
 * 走本地线程池时就只跑一次。这样执行逻辑与调度方式解耦，两条路径共用同一份代码。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TranscodeTaskRunner {

    /** 任务状态：1-处理中 2-成功 3-失败 */
    private static final int STATUS_PROCESSING = 1;
    private static final int STATUS_SUCCESS = 2;
    private static final int STATUS_FAILED = 3;
    /** VideoInfo.status = 3 表示已发布（可播放） */
    private static final int VIDEO_PUBLISHED = 3;

    private final TranscodeTaskService taskService;
    private final VideoInfoService videoInfoService;
    private final StorageService storageService;
    private final FfmpegService ffmpegService;
    private final TranscodeProperties transcodeProperties;
    private final FfmpegProperties ffmpegProperties;

    /**
     * 执行一次转码。成功置为 SUCCESS；失败抛异常且<b>不写终态</b>，由调用方决定重投还是终结。
     * <p>MQ 是至少一次投递，消息可能重复到达，因此已是终态的任务直接跳过。</p>
     */
    public void run(Long taskId) {
        TranscodeTask task = taskService.getById(taskId);
        if (task == null) {
            log.warn("转码任务不存在，忽略 taskId={}", taskId);
            return;
        }
        Integer status = task.getStatus();
        if (status != null && (status == STATUS_SUCCESS || status == STATUS_FAILED)) {
            log.info("转码任务已是终态，跳过 taskId={} status={}", taskId, status);
            return;
        }

        doTranscode(task);

        task.setStatus(STATUS_SUCCESS);
        task.setProgress(100);
        task.setErrorMsg(null);
        task.setFinishedAt(LocalDateTime.now());
        taskService.updateById(task);
        log.info("转码完成 taskId={} videoKey={}", taskId, task.getVideoKey());
    }

    /** 把任务终结为失败态（重试次数耗尽或本地执行失败时调用） */
    public void markFailed(Long taskId, String errorMsg) {
        TranscodeTask task = taskService.getById(taskId);
        if (task == null) {
            return;
        }
        task.setStatus(STATUS_FAILED);
        task.setErrorMsg(truncate(errorMsg));
        task.setFinishedAt(LocalDateTime.now());
        taskService.updateById(task);
        log.error("转码任务失败终结 taskId={} error={}", taskId, errorMsg);
    }

    private void doTranscode(TranscodeTask task) {
        int retryCount = task.getRetryCount() == null ? 0 : task.getRetryCount();
        // 领取时已是「处理中」，说明上一次执行中断了（broker 重投或本地重试），计一次重试
        if (task.getStatus() != null && task.getStatus() == STATUS_PROCESSING) {
            retryCount++;
        }
        task.setStatus(STATUS_PROCESSING);
        task.setRetryCount(retryCount);
        task.setProgress(5);
        taskService.updateById(task);

        VideoInfo video = videoInfoService.getById(task.getVideoId());
        if (video == null) {
            throw new BizException(ResultCode.NOT_FOUND, "视频不存在");
        }
        if (StringUtils.isBlank(video.getStoragePath())) {
            throw new BizException(ResultCode.VALIDATE_FAILED, "视频尚未上传文件，无法转码");
        }

        // 1. 拉取源片到本地临时文件
        Path src = storageService.fetchToTempFile(video.getStoragePath());
        task.setProgress(20);
        taskService.updateById(task);

        // 2. 转码为多清晰度 HLS（输出到 workDir/transcode/{videoKey}）
        Path outDir = Paths.get(ffmpegProperties.getWorkDir(), "transcode", video.getVideoKey());
        Path master = ffmpegService.transcodeToAdaptiveHls(src, outDir,
                transcodeProperties.getRenditions(),
                transcodeProperties.getHwaccel(),
                transcodeProperties.getSegmentType(),
                transcodeProperties.getHlsTime(),
                transcodeProperties.getMasterName());
        task.setProgress(70);
        taskService.updateById(task);

        // 3. 上传 HLS 产物到对象存储（CDN 可回源）
        String hlsPrefix = "video/" + video.getVideoKey() + "/hls";
        storageService.uploadDir(outDir, hlsPrefix);
        String masterObject = hlsPrefix + "/" + transcodeProperties.getMasterName();
        String hlsUrl = storageService.publicUrl(masterObject);
        task.setHlsPath(masterObject);
        task.setRenditions(renditionNames());
        task.setProgress(90);
        taskService.updateById(task);

        // 4. 抽取封面并上传（失败不阻断）
        try {
            Path cover = ffmpegService.thumbnail(src, video.getVideoKey() + ".jpg");
            String coverObject = "video/" + video.getVideoKey() + "/cover.jpg";
            try (InputStream in = Files.newInputStream(cover)) {
                storageService.upload(coverObject, in, "image/jpeg", Files.size(cover));
            }
            video.setCoverUrl(storageService.publicUrl(coverObject));
        } catch (Exception e) {
            log.warn("封面抽取失败 videoKey={}", video.getVideoKey(), e);
        }

        // 5. 回写视频并置为已发布
        video.setHlsUrl(hlsUrl);
        video.setStatus(VIDEO_PUBLISHED);
        video.setPublishTime(LocalDateTime.now());
        videoInfoService.updateById(video);

        log.debug("HLS 产物已上传 master={}", master);
    }

    /** 已生成档位名，形如 ["1080p","720p","480p","360p"] */
    private String renditionNames() {
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (TranscodeProperties.Rendition r : transcodeProperties.getRenditions()) {
            if (!first) {
                sb.append(",");
            }
            sb.append("\"").append(r.getName()).append("\"");
            first = false;
        }
        return sb.append("]").toString();
    }

    private static String truncate(String msg) {
        if (msg == null) {
            return null;
        }
        return msg.length() > 1000 ? msg.substring(0, 1000) : msg;
    }
}
