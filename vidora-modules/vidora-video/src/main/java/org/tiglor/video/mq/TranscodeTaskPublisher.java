package org.tiglor.video.mq;

import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.common.message.Message;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.tiglor.video.config.TranscodeProperties;
import org.tiglor.video.service.impl.TranscodeTaskRunner;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executor;

/**
 * 转码任务投递：优先走 MQ（持久化，进程重启不丢在途任务），未启用或投递失败时退回本地线程池。
 * <p>
 * 退回是刻意的——MQ 故障时视频不该从此不再转码；代价是此时退化回「重启丢任务」的旧行为，
 * 因此退回一定伴随 WARN/ERROR 日志。
 * </p>
 */
@Slf4j
@Component
public class TranscodeTaskPublisher {

    private final MqProperties mqProperties;
    private final TranscodeProperties transcodeProperties;
    private final TranscodeTaskRunner runner;
    /** 未启用 MQ 时容器里没有该 bean，用 ObjectProvider 做可选依赖 */
    private final ObjectProvider<DefaultMQProducer> producerProvider;
    private final Executor transcodeExecutor;

    public TranscodeTaskPublisher(MqProperties mqProperties,
                                  TranscodeProperties transcodeProperties,
                                  TranscodeTaskRunner runner,
                                  ObjectProvider<DefaultMQProducer> producerProvider,
                                  @Qualifier("transcodeExecutor") Executor transcodeExecutor) {
        this.mqProperties = mqProperties;
        this.transcodeProperties = transcodeProperties;
        this.runner = runner;
        this.producerProvider = producerProvider;
        this.transcodeExecutor = transcodeExecutor;
    }

    /** 投递任务；消息体就是 taskId，任务详情一律以数据库为准 */
    public void publish(Long taskId) {
        DefaultMQProducer producer = producerProvider.getIfAvailable();
        if (producer == null) {
            log.debug("未启用 RocketMQ，转码任务走本地线程池 taskId={}", taskId);
            runLocally(taskId);
            return;
        }
        try {
            Message message = new Message(mqProperties.getTranscodeTopic(),
                    String.valueOf(taskId).getBytes(StandardCharsets.UTF_8));
            // keys 便于在 RocketMQ 控制台按任务 ID 检索消息轨迹
            message.setKeys(String.valueOf(taskId));
            SendResult result = producer.send(message, mqProperties.getSendTimeoutMillis());
            if (result.getSendStatus() != SendStatus.SEND_OK) {
                log.warn("转码任务投递未获 broker 确认，退回本地线程池 taskId={} status={}",
                        taskId, result.getSendStatus());
                runLocally(taskId);
                return;
            }
            log.info("转码任务已投递 MQ taskId={} msgId={}", taskId, result.getMsgId());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("转码任务投递被中断，退回本地线程池 taskId={}", taskId, e);
            runLocally(taskId);
        } catch (Exception e) {
            log.error("转码任务投递 MQ 失败，退回本地线程池 taskId={}", taskId, e);
            runLocally(taskId);
        }
    }

    private void runLocally(Long taskId) {
        transcodeExecutor.execute(() -> {
            // 本地路径没有 broker 重投，重试只能自己来；沿用未启用 MQ 时的原有次数配置
            int attempts = Math.max(1, transcodeProperties.getMaxRetry() + 1);
            for (int attempt = 1; attempt <= attempts; attempt++) {
                try {
                    runner.run(taskId);
                    return;
                } catch (Exception e) {
                    if (attempt == attempts) {
                        log.error("本地转码执行失败，已尝试 {} 次 taskId={}", attempts, taskId, e);
                        runner.markFailed(taskId, e.getMessage());
                        return;
                    }
                    log.warn("本地转码执行失败，准备重试 taskId={} attempt={}/{}", taskId, attempt, attempts, e);
                }
            }
        });
    }
}
