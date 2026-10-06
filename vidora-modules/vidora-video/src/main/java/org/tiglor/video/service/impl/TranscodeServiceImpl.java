package org.tiglor.video.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.tiglor.common.core.BizException;
import org.tiglor.common.core.ResultCode;
import org.tiglor.video.config.TranscodeProperties;
import org.tiglor.video.entity.TranscodeTask;
import org.tiglor.video.entity.VideoInfo;
import org.tiglor.video.mq.TranscodeTaskPublisher;
import org.tiglor.video.service.TranscodeService;
import org.tiglor.video.service.TranscodeTaskService;
import org.tiglor.video.service.VideoInfoService;

import java.time.LocalDateTime;

/**
 * 转码任务的提交与查询。
 * <p>
 * 本类只负责「建任务 + 投递」，真正执行在 {@link TranscodeTaskRunner}：
 * 启用 RocketMQ 时由 broker 推送给消费线程（重启后在途任务不丢），
 * 未启用时退回 {@code transcodeExecutor} 线程池。投递细节见 {@link TranscodeTaskPublisher}。
 * </p>
 * <p>
 * {@link #submit(Long)} 还兼作僵死任务的恢复入口：未启用 MQ 时执行进程中途崩溃
 * （OOM / kill -9 / 重启）会让任务永远停在「处理中」，此时重新提交即接管重投。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TranscodeServiceImpl implements TranscodeService {

    /** 任务状态：0-待处理 1-处理中 3-失败 */
    private static final int STATUS_PENDING = 0;
    private static final int STATUS_PROCESSING = 1;
    private static final int STATUS_FAILED = 3;

    private final TranscodeTaskService taskService;
    private final VideoInfoService videoInfoService;
    private final TranscodeTaskPublisher publisher;
    private final TranscodeProperties transcodeProperties;

    @Override
    public TranscodeTask submit(Long videoId) {
        VideoInfo v = videoInfoService.getById(videoId);
        if (v == null) {
            throw new BizException(ResultCode.NOT_FOUND, "视频不存在");
        }
        if (StringUtils.isBlank(v.getStoragePath())) {
            throw new BizException(ResultCode.VALIDATE_FAILED, "视频尚未上传文件，无法转码");
        }

        // 幂等：已有待处理/处理中任务则直接返回，不重复投递。
        // 但「处理中」要分两种看：真在跑的，和执行进程已经没了、永远停在那儿的。
        // 后者照样返回的话，调用方会以为任务还活着，而视频从此再也转不出来
        TranscodeTask running = taskService.getOne(new LambdaQueryWrapper<TranscodeTask>()
                .eq(TranscodeTask::getVideoId, videoId)
                .in(TranscodeTask::getStatus, STATUS_PENDING, STATUS_PROCESSING)
                .orderByDesc(TranscodeTask::getId)
                .last("limit 1"));
        if (running != null) {
            return isStale(running) ? takeOverStaleTask(running) : running;
        }

        TranscodeTask task = new TranscodeTask();
        task.setVideoId(videoId);
        task.setVideoKey(v.getVideoKey());
        task.setSourcePath(v.getStoragePath());
        task.setStatus(STATUS_PENDING);
        task.setProgress(0);
        task.setRetryCount(0);
        taskService.save(task);

        // 必须先落库再投递：消费者只拿到 taskId，记录查不到就会白跑一趟
        publisher.publish(task.getId());
        return task;
    }

    @Override
    public TranscodeTask latest(Long videoId) {
        return taskService.getOne(new LambdaQueryWrapper<TranscodeTask>()
                .eq(TranscodeTask::getVideoId, videoId)
                .orderByDesc(TranscodeTask::getId)
                .last("limit 1"));
    }

    /**
     * 「处理中」是否已经僵死：距上次进度写入超过阈值就算。
     * <p>
     * 依据是 {@code doTranscode} 每过一个阶段都会 updateById 刷进度，
     * MyBatis-Plus 的 AutoFillHandler 顺带把 update_time 写成当前时间，
     * 所以它冻结不动就说明没人在推进这个任务。阈值见
     * {@link TranscodeProperties#getStaleProcessingMinutes()}——给得偏宽是刻意的，
     * 误判会把健康任务重投一遍，变成两个 ffmpeg 抢同一个输出目录。
     * </p>
     */
    private boolean isStale(TranscodeTask task) {
        if (task.getStatus() == null || task.getStatus() != STATUS_PROCESSING) {
            return false;
        }
        LocalDateTime lastTouch = task.getUpdateTime();
        if (lastTouch == null) {
            return true;
        }
        return lastTouch.isBefore(LocalDateTime.now().minusMinutes(transcodeProperties.getStaleProcessingMinutes()));
    }

    /**
     * 接管僵死任务：重置为待处理后重新投递。
     * <p>
     * 重试次数用尽就地终结为失败，而不是继续重投——否则每次点「重新转码」都会再跑一遍
     * 完整 ffmpeg，任务永远收不了场，管理端也看不到「这个视频转不出来」这个结论。
     * </p>
     */
    private TranscodeTask takeOverStaleTask(TranscodeTask task) {
        int retryCount = (task.getRetryCount() == null ? 0 : task.getRetryCount()) + 1;
        task.setRetryCount(retryCount);
        if (retryCount > transcodeProperties.getMaxRetry()) {
            log.warn("僵死转码任务重试次数已用尽，终结为失败 taskId={} retryCount={}", task.getId(), retryCount);
            task.setStatus(STATUS_FAILED);
            task.setErrorMsg("转码进程中断，重试 " + retryCount + " 次后仍未完成");
            task.setFinishedAt(LocalDateTime.now());
            taskService.updateById(task);
            return task;
        }
        log.warn("发现僵死转码任务，重置后重新投递 taskId={} videoId={} retryCount={}",
                task.getId(), task.getVideoId(), retryCount);
        task.setStatus(STATUS_PENDING);
        task.setProgress(0);
        task.setErrorMsg(null);
        taskService.updateById(task);
        publisher.publish(task.getId());
        return task;
    }
}
