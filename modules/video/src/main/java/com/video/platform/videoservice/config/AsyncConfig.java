package com.video.platform.videoservice.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 异步配置：为转码任务提供独立线程池（transcodeExecutor）。
 * <p>
 * 生产环境更优做法是把转码任务投递到消息队列（RocketMQ/Kafka）+ 独立转码集群/云 MPS，
 * 由 worker 消费执行；本实现用 Spring {@code @Async} 线程池作为单体/原型阶段的可运行替代，
 * 接口与状态机结构保持一致，后续可平滑替换为 MQ 模式。
 * </p>
 */
@Configuration
@EnableAsync
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
