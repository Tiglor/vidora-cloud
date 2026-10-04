package org.tiglor.video.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.tiglor.common.core.BizException;
import org.tiglor.common.core.ResultCode;
import org.tiglor.video.entity.TranscodeTask;
import org.tiglor.video.entity.VideoInfo;
import org.tiglor.video.mq.TranscodeTaskPublisher;
import org.tiglor.video.service.TranscodeService;
import org.tiglor.video.service.TranscodeTaskService;
import org.tiglor.video.service.VideoInfoService;

/**
 * 转码任务的提交与查询。
 * <p>
 * 本类只负责「建任务 + 投递」，真正执行在 {@link TranscodeTaskRunner}：
 * 启用 RocketMQ 时由 broker 推送给消费线程（重启后在途任务不丢），
 * 未启用时退回 {@code transcodeExecutor} 线程池。投递细节见 {@link TranscodeTaskPublisher}。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TranscodeServiceImpl implements TranscodeService {

    /** 任务状态：0-待处理 1-处理中 */
    private static final int STATUS_PENDING = 0;
    private static final int STATUS_PROCESSING = 1;

    private final TranscodeTaskService taskService;
    private final VideoInfoService videoInfoService;
    private final TranscodeTaskPublisher publisher;

    @Override
    public TranscodeTask submit(Long videoId) {
        VideoInfo v = videoInfoService.getById(videoId);
        if (v == null) {
            throw new BizException(ResultCode.NOT_FOUND, "视频不存在");
        }
        if (StringUtils.isBlank(v.getStoragePath())) {
            throw new BizException(ResultCode.VALIDATE_FAILED, "视频尚未上传文件，无法转码");
        }

        // 幂等：已有待处理/处理中任务则直接返回，不重复投递
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
}
