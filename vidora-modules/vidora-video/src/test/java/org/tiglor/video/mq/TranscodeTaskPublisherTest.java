package org.tiglor.video.mq;

import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.tiglor.video.config.TranscodeProperties;
import org.tiglor.video.service.impl.TranscodeTaskRunner;

import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 覆盖「未启用 MQ」这条默认路径——本地开发和多数部署都走它，且不需要 broker 就能验证。
 * 启用 MQ 后的收发链路依赖真实 broker，不在单测覆盖范围内。
 */
class TranscodeTaskPublisherTest {

    /** 同线程执行，省掉等待与线程调度的不确定性 */
    private static final Executor DIRECT = Runnable::run;

    private final TranscodeTaskRunner runner = mock(TranscodeTaskRunner.class);
    private final TranscodeProperties transcodeProperties = new TranscodeProperties();

    @SuppressWarnings("unchecked")
    private TranscodeTaskPublisher publisherWithNoBroker() {
        ObjectProvider<DefaultMQProducer> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);
        return new TranscodeTaskPublisher(new MqProperties(), transcodeProperties, runner, provider, DIRECT);
    }

    @Test
    @DisplayName("未启用 MQ 时，任务落到本地线程池执行")
    void fallsBackToLocalExecutorWhenMqDisabled() {
        publisherWithNoBroker().publish(7L);

        verify(runner).run(7L);
        verify(runner, never()).markFailed(anyLong(), anyString());
    }

    @Test
    @DisplayName("本地执行失败按 max-retry 重试，耗尽后把任务置为失败")
    void retriesThenMarksFailedWhenLocalRunKeepsFailing() {
        transcodeProperties.setMaxRetry(2);
        doThrow(new IllegalStateException("ffmpeg 未安装")).when(runner).run(anyLong());

        publisherWithNoBroker().publish(7L);

        // max-retry=2 表示首次之外再试 2 次
        verify(runner, times(3)).run(7L);
        verify(runner).markFailed(7L, "ffmpeg 未安装");
    }

    @Test
    @DisplayName("max-retry=0 时只跑一次，不重试")
    void runsOnceWhenMaxRetryIsZero() {
        transcodeProperties.setMaxRetry(0);
        doThrow(new IllegalStateException("boom")).when(runner).run(anyLong());

        publisherWithNoBroker().publish(7L);

        verify(runner, times(1)).run(7L);
        verify(runner).markFailed(7L, "boom");
    }

    @Test
    @DisplayName("未配置 rocketmq.enabled 时不创建任何 MQ 客户端（本地开发无需 broker）")
    void createsNoMqBeansByDefault() {
        new ApplicationContextRunner()
                .withUserConfiguration(RocketMqConfig.class)
                .withBean(MqProperties.class, MqProperties::new)
                .run(context -> {
                    assertThat(context).doesNotHaveBean(DefaultMQProducer.class);
                    assertThat(context).doesNotHaveBean("transcodeConsumer");
                });
    }

    @Test
    @DisplayName("显式 rocketmq.enabled=false 时同样不创建 MQ 客户端")
    void createsNoMqBeansWhenExplicitlyDisabled() {
        new ApplicationContextRunner()
                .withUserConfiguration(RocketMqConfig.class)
                .withBean(MqProperties.class, MqProperties::new)
                .withPropertyValues("rocketmq.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(DefaultMQProducer.class));
    }
}
