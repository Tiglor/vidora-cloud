package org.tiglor.video.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 转码线程池（transcodeExecutor）。
 * <p>
 * RocketMQ 已接入（见 {@code org.tiglor.video.mq}），启用后转码在 MQ 的消费线程里执行，
 * 本线程池只在两种情况下用到：{@code rocketmq.enabled=false}，或消息投递失败时的兜底。
 * 因此并发度与 {@code rocketmq.consume-thread-max} 保持同一量级即可。
 * </p>
 */
@Configuration
@RequiredArgsConstructor
public class AsyncConfig {

    private final TranscodeProperties transcodeProperties;

    @Bean("transcodeExecutor")
    public Executor transcodeExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(transcodeProperties.getCorePoolSize());
        executor.setMaxPoolSize(transcodeProperties.getMaxPoolSize());
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("transcode-");
        // 队列满时由调用方线程执行，避免任务静默丢失
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.initialize();
        return executor;
    }
}
