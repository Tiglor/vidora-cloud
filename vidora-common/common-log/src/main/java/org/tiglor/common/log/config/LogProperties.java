package org.tiglor.common.log.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 日志与审计相关配置，前缀 {@code vidora.log}。
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "vidora.log")
public class LogProperties {

    /** 日志根目录，最终落在 ${path}/<服务名>/{app,error,audit}.log。容器里挂到卷上 */
    private String path = "logs";

    /** 单个文件上限与保留策略，交给 logback-base.xml 读取 */
    private String maxFileSize = "100MB";
    private int maxHistory = 30;
    private String totalSizeCap = "5GB";

    /** 操作审计总开关 */
    private boolean operEnabled = true;

    /**
     * 是否把审计上报给 system-service 落库。
     * 关掉只影响管理端「日志管理」页面能不能查到，audit.log 仍然会写，数据不丢。
     */
    private boolean ingestEnabled = true;

    /** 审计落库服务（Nacos 服务名），走内网直连不经网关 */
    private String ingestServiceId = "system-service";

    /** 内网写入接口路径前缀 */
    private String ingestPath = "/internal/logs";

    /**
     * 内网写入口令。非空时上报请求会带 X-Ingest-Token，接收侧必须校验一致。
     * 默认空 = 不校验，仅限本机开发；docker-compose 里由环境变量注入。
     */
    private String ingestToken = "";

    /** 参数 / 返回值 / 错误信息各自的最大落库长度，超出截断 */
    private int maxParamLength = 2000;
    private int maxResultLength = 2000;
    private int maxErrorLength = 2000;

    /** 上报线程池：队列打满后业务线程自己执行（宁慢勿丢） */
    private int ingestQueueCapacity = 1000;
    private int ingestMaxThreads = 2;

    /** 连接 / 读取超时（毫秒） */
    private int connectTimeoutMs = 2000;
    private int readTimeoutMs = 5000;
}
