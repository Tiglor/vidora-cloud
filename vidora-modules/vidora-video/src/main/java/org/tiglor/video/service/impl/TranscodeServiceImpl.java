package org.tiglor.video.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import org.tiglor.common.core.BizException;
import org.tiglor.common.core.ResultCode;
import org.tiglor.video.config.FfmpegProperties;
import org.tiglor.video.config.TranscodeProperties;
import org.tiglor.video.entity.TranscodeTask;
import org.tiglor.video.entity.VideoInfo;
import org.tiglor.video.service.StorageService;
import org.tiglor.video.service.TranscodeService;
import org.tiglor.video.service.TranscodeTaskService;
import org.tiglor.video.service.VideoInfoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;

/**
 * 异步转码服务（大厂思路：上传落存储 → 提交任务 → 转码 Worker 池跑 ffmpeg 出多清晰度 HLS → 回传对象存储 → 暴露主播放地址）。
 * <p>
 * 与业务请求线程解耦：{@link #submit(Long)} 仅建任务并投递，真正的转码在 {@code transcodeExecutor} 线程池执行；
 * 前端通过 {@link #latest(Long)} 轮询任务状态/进度。失败按 maxRetry 自动重试。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TranscodeServiceImpl implements TranscodeService {

    /** 任务状态 */
    private static final int STATUS_PENDING = 0;
    private static final int STATUS_PROCESSING = 1;
    private static final int STATUS_SUCCESS = 2;
    private static final int STATUS_FAILED = 3;
    /** VideoInfo.status = 3 表示已发布（可播放） */
    private static final int STATUS_PUBLISHED = 3;

    private final TranscodeTaskService taskService;
    private final VideoInfoService videoInfoService;
    private final StorageService storageService;
    private final FfmpegService ffmpegService;
    private final TranscodeProperties transcodeProperties;
    private final FfmpegProperties ffmpegProperties;

    /** 自注入代理，确保 @Async 在重试时仍走线程池 */
    @org.springframework.context.annotation.Lazy
    @Autowired
    private TranscodeService self;

    @Override
    public TranscodeTask submit(Long videoId) {
        VideoInfo v = videoInfoService.getById(videoId);
        if (v == null) {
            throw new BizException(ResultCode.NOT_FOUND, "视频不存在");
        }
        if (StringUtils.isBlank(v.getStoragePath())) {
            throw new BizException(ResultCode.FAIL, "视频尚未上传文件，无法转码");
        }

        // 幂等：已有待处理/处理中任务则直接返回
        TranscodeTask running = taskService.getOne(new LambdaQueryWrapper<TranscodeTask>()
                .eq(TranscodeTask::getVideoId, videoId)
                .in(TranscodeTask::getStatus, STATUS_PENDING, STATUS_PROCESSING)
                .orderByDesc(TranscodeTask::getId)
                .last("limit 1"));
        if (running != null) {
            return running;
        }

        TranscodeTask task = new TranscodeTask();
        task.setVideoId(videoId);
        task.setVideoKey(v.getVideoKey());
        task.setSourcePath(v.getStoragePath());
        task.setStatus(STATUS_PENDING);
        task.setProgress(0);
        task.setRetryCount(0);
        taskService.save(task);

        self.executeAsync(task.getId());
        return task;
    }

    @Override
    public TranscodeTask latest(Long videoId) {
        return taskService.getOne(new LambdaQueryWrapper<TranscodeTask>()
                .eq(TranscodeTask::getVideoId, videoId)
                .orderByDesc(TranscodeTask::getId)
                .last("limit 1"));
    }

    @Async("transcodeExecutor")
    public void executeAsync(Long taskId) {
        TranscodeTask task = taskService.getById(taskId);
        if (task == null) {
            return;
        }
        try {
            doTranscode(task);
        } catch (Exception e) {
            log.error("转码任务失败 taskId={}", taskId, e);
            int retry = task.getRetryCount() == null ? 0 : task.getRetryCount();
            if (retry < transcodeProperties.getMaxRetry()) {
                task.setStatus(STATUS_PENDING);
                task.setRetryCount(retry + 1);
                task.setProgress(0);
                task.setErrorMsg(null);
                taskService.updateById(task);
                log.info("转码任务重试 taskId={} retry={}", taskId, task.getRetryCount());
                self.executeAsync(task.getId());
            } else {
                task.setStatus(STATUS_FAILED);
                task.setErrorMsg(truncate(e.getMessage()));
                taskService.updateById(task);
            }
        }
    }

    private void doTranscode(TranscodeTask task) {
        task.setStatus(STATUS_PROCESSING);
        task.setProgress(5);
        taskService.updateById(task);

        VideoInfo video = videoInfoService.getById(task.getVideoId());
        if (video == null) {
            throw new BizException(ResultCode.NOT_FOUND, "视频不存在");
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
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (TranscodeProperties.Rendition r : transcodeProperties.getRenditions()) {
            if (!first) {
                sb.append(",");
            }
            sb.append("\"").append(r.getName()).append("\"");
            first = false;
        }
        sb.append("]");
        task.setRenditions(sb.toString());
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
        video.setStatus(STATUS_PUBLISHED);
        video.setPublishTime(LocalDateTime.now());
        videoInfoService.updateById(video);

        task.setProgress(100);
        task.setStatus(STATUS_SUCCESS);
        task.setFinishedAt(LocalDateTime.now());
        taskService.updateById(task);
        log.info("转码完成 videoKey={} hlsUrl={}", video.getVideoKey(), hlsUrl);
    }

    private static String truncate(String msg) {
        if (msg == null) {
            return null;
        }
        return msg.length() > 1000 ? msg.substring(0, 1000) : msg;
    }
}
