package org.tiglor.common.log.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import org.springframework.context.annotation.Bean;
import org.tiglor.common.log.aspect.OperLogAspect;
import org.tiglor.common.log.sink.FileLogSink;
import org.tiglor.common.log.sink.LogSink;
import org.tiglor.common.log.sink.RemoteLogSink;
import org.tiglor.common.log.trace.TraceIdFilter;

/**
 * common-log 的自动配置。
 * <p>
 * 刻意用 {@code @AutoConfiguration} 而不是靠各服务的 scanBasePackages 扫到：
 * 日志设施是横切的，谁引入了 jar 就该有谁的行为，不该取决于启动类扫了哪些包。
 * 整体限定在 Servlet 应用下生效，WebFlux 网关只用 jar 里的 logback-base.xml。
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties(LogProperties.class)
public class CommonLogAutoConfiguration {

    /** traceId 要在任何业务日志打出来之前就位，优先级见 TraceIdFilter 上的 @Order */
    @Bean
    @ConditionalOnMissingBean
    public TraceIdFilter traceIdFilter() {
        return new TraceIdFilter();
    }

    /**
     * 审计去向。两个 Bean 由 ingest-enabled 二选一，且都让位于使用方自定义的 LogSink：
     * system-service 自己就是审计的落库方，它注入的那个 Bean 直接写库，
     * 不需要绕一圈 HTTP 调自己。
     */
    @Bean
    @ConditionalOnMissingBean(LogSink.class)
    @ConditionalOnProperty(prefix = "vidora.log", name = "ingest-enabled", havingValue = "false")
    public LogSink fileLogSink() {
        return new FileLogSink();
    }

    @Bean(destroyMethod = "shutdown")
    @ConditionalOnMissingBean(LogSink.class)
    @ConditionalOnProperty(prefix = "vidora.log", name = "ingest-enabled",
            havingValue = "true", matchIfMissing = true)
    public LogSink remoteLogSink(LogProperties props, ObjectProvider<LoadBalancerClient> loadBalancer) {
        // 上报失败退回文件，所以 FileLogSink 是它的内部依赖而不是并列的 Bean
        return new RemoteLogSink(props, loadBalancer.getIfAvailable(), new FileLogSink());
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "vidora.log", name = "oper-enabled", matchIfMissing = true)
    public OperLogAspect operLogAspect(LogSink logSink, LogProperties props) {
        return new OperLogAspect(logSink, props);
    }
}
