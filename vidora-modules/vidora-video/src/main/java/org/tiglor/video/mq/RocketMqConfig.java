package org.tiglor.video.mq;

import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.common.consumer.ConsumeFromWhere;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.tiglor.video.service.impl.TranscodeTaskRunner;

/**
 * RocketMQ 原生客户端装配。
 * <p>
 * 不用 rocketmq-spring-boot-starter：它按 Spring Framework 5.3.27 编译、未适配 Boot 4，
 * 所以这里直接管理 {@code DefaultMQProducer} / {@code DefaultMQPushConsumer} 的生命周期。
 * </p>
 * <p>
 * 仅在 {@code rocketmq.enabled=true} 时生效；未启用时容器里没有 producer bean，
 * {@link TranscodeTaskPublisher} 会自动回落到本地线程池，本地开发无需起 broker。
 * </p>
 */
@Slf4j
@Configuration
@ConditionalOnProperty(prefix = "rocketmq", name = "enabled", havingValue = "true")
public class RocketMqConfig {

    @Bean(destroyMethod = "shutdown")
    public DefaultMQProducer transcodeProducer(MqProperties properties) throws MQClientException {
        DefaultMQProducer producer = new DefaultMQProducer(properties.getProducerGroup());
        producer.setNamesrvAddr(properties.getNameServer());
        producer.setSendMsgTimeout(properties.getSendTimeoutMillis());
        // 启动失败就让容器起不来：显式打开了 MQ 却连不上，静默降级会掩盖故障
        producer.start();
        log.info("RocketMQ Producer 已启动 group={} nameServer={}",
                properties.getProducerGroup(), properties.getNameServer());
        return producer;
    }

    @Bean(destroyMethod = "shutdown")
    public DefaultMQPushConsumer transcodeConsumer(MqProperties properties, TranscodeTaskRunner runner)
            throws MQClientException {
        DefaultMQPushConsumer consumer = new DefaultMQPushConsumer(properties.getConsumerGroup());
        consumer.setNamesrvAddr(properties.getNameServer());
        consumer.setConsumeThreadMin(properties.getConsumeThreadMin());
        consumer.setConsumeThreadMax(properties.getConsumeThreadMax());
        consumer.setMaxReconsumeTimes(properties.getMaxReconsumeTimes());
        // 从上次位点继续：重启后接着消费在途任务（这正是要的效果），
        // 但消费组首次创建时不回放历史消息，避免把老任务重新转一遍
        consumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_LAST_OFFSET);
        consumer.subscribe(properties.getTranscodeTopic(), "*");
        consumer.registerMessageListener(new TranscodeTaskListener(properties, runner));
        consumer.start();
        log.info("RocketMQ Consumer 已启动 group={} topic={} nameServer={}",
                properties.getConsumerGroup(), properties.getTranscodeTopic(), properties.getNameServer());
        return consumer;
    }
}
