package org.tiglor.video.service;

import org.tiglor.video.entity.TranscodeTask;

public interface TranscodeService {

    /**
     * 提交转码任务（幂等：同视频已有待处理/处理中任务则直接返回该任务）。
     * <p>
     * 任务先落库再投递，执行在别处发生（MQ 消费线程或本地转码线程池），
     * 调用方应立即返回并通过 {@link #latest(Long)} 轮询状态。
     * </p>
     */
    TranscodeTask submit(Long videoId);

    /**
     * 查询某视频最新的转码任务（用于前端轮询状态/进度）。
     */
    TranscodeTask latest(Long videoId);
}
