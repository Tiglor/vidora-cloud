package org.tiglor.video.mq;

import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyContext;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.common.message.MessageExt;
import org.tiglor.video.service.impl.TranscodeTaskRunner;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 转码任务消费者：在 MQ 的消费线程里<b>同步</b>跑完转码再 ack。
 * <p>
 * 这里不能调 @Async 方法——那会立刻返回并 ack，任务实际还在跑，
 * 一旦进程重启就丢掉，等于没接 MQ。同步执行才能让失败通过 RECONSUME_LATER 交给 broker 重投。
 * </p>
 */
@Slf4j
public class TranscodeTaskListener implements MessageListenerConcurrently {

    private final MqProperties properties;
    private final TranscodeTaskRunner runner;

    public TranscodeTaskListener(MqProperties properties, TranscodeTaskRunner runner) {
        this.properties = properties;
        this.runner = runner;
    }

    @Override
    public ConsumeConcurrentlyStatus consumeMessage(List<MessageExt> msgs, ConsumeConcurrentlyContext context) {
        for (MessageExt msg : msgs) {
            Long taskId = parseTaskId(msg);
            if (taskId == null) {
                // 消息体损坏，重投也永远不会成功，直接丢弃避免堵死队列
                log.error("转码消息体非法，丢弃 msgId={} body={}", msg.getMsgId(), new String(msg.getBody(), StandardCharsets.UTF_8));
                continue;
            }
            try {
                runner.run(taskId);
            } catch (Exception e) {
                if (msg.getReconsumeTimes() >= properties.getMaxReconsumeTimes()) {
                    // 重投耗尽：写终态并 ack，否则前端会一直轮询到「处理中」
                    log.error("转码任务重试耗尽 taskId={} reconsumeTimes={}", taskId, msg.getReconsumeTimes(), e);
                    runner.markFailed(taskId, e.getMessage());
                    continue;
                }
                log.warn("转码任务执行失败，交回 broker 重投 taskId={} reconsumeTimes={}",
                        taskId, msg.getReconsumeTimes(), e);
                return ConsumeConcurrentlyStatus.RECONSUME_LATER;
            }
        }
        return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
    }

    private static Long parseTaskId(MessageExt msg) {
        byte[] body = msg.getBody();
        if (body == null || body.length == 0) {
            return null;
        }
        try {
            return Long.valueOf(new String(body, StandardCharsets.UTF_8).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
