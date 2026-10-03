package com.video.platform.videoservice.service;

import com.video.platform.videoservice.entity.TranscodeTask;

public interface TranscodeService {

    /**
     * 提交转码任务（幂等：同视频已有待处理/处理中任务则直接返回该任务）。
     * 内部触发异步执行，调用方应立即返回并通过 {@link #latest(Long)} 轮询状态。
     */
    TranscodeTask submit(Long videoId);

    /**
     * 查询某视频最新的转码任务（用于前端轮询状态/进度）。
     */
    TranscodeTask latest(Long videoId);

    /**
     * 内部：异步执行转码（由 {@link #submit(Long)} 触发，前端不直接调用）。
     */
    void executeAsync(Long taskId);
}
