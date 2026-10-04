package org.tiglor.video.mq;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * RocketMQ 配置。
 * <p>
 * {@code enabled=false}（默认）时不创建任何 MQ bean，转码任务回落到本地 {@code transcodeExecutor}
 * 线程池执行——功能可用，但进程重启会丢掉在途任务，这正是引入 MQ 要解决的问题。
 * </p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "rocketmq")
public class MqProperties {

    /** 是否启用 MQ 投递；关闭则走本地线程池 */
    private boolean enabled = false;

    /** NameServer 地址，多个用分号分隔 */
    private String nameServer = "127.0.0.1:9876";

    private String producerGroup = "vidora-video-transcode-producer";
    private String consumerGroup = "vidora-video-transcode-consumer";

    /** 转码任务 Topic */
    private String transcodeTopic = "vidora-transcode-task";

    /** 同步发送超时（毫秒） */
    private int sendTimeoutMillis = 3000;

    /**
     * 消费失败后由 broker 重投的最大次数；超过则把任务置为失败并 ack，
     * 避免任务永远停在「处理中」而前端轮询不到终态
     */
    private int maxReconsumeTimes = 3;

    /** 消费线程数：转码是 CPU/IO 重活，与 ffmpeg 并发度对齐即可，不宜过大 */
    private int consumeThreadMin = 2;
    private int consumeThreadMax = 4;
}
