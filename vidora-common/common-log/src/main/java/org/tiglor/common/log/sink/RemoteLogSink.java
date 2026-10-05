package org.tiglor.common.log.sink;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.client.loadbalancer.LoadBalancerInterceptor;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;
import org.tiglor.common.log.config.LogProperties;
import org.tiglor.common.log.entity.LoginLogEntity;
import org.tiglor.common.log.entity.OperLogEntity;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 审计上报：异步 POST 到 system-service 的内网写入口。
 * <p>
 * 三件事决定了它的形状：
 * <ol>
 *   <li>不能阻塞业务 —— 所以丢进独立线程池，请求线程只是 submit；</li>
 *   <li>不能丢数据 —— 队列满时用 CallerRunsPolicy 让业务线程自己发（宁慢勿丢），
 *       上报失败再退回 {@link FileLogSink} 写 audit.log；</li>
 *   <li>不能污染业务语义 —— 上报路径走 {@code /internal/**}，网关不路由这类前缀，
 *       只在容器网络内可达，因此不需要用户身份；带一个可选的共享口令兜住越权。</li>
 * </ol>
 */
@Slf4j
public class RemoteLogSink implements LogSink {

    /** 内网写入口令头，与 system-service 侧校验保持一致 */
    public static final String INGEST_TOKEN_HEADER = "X-Ingest-Token";

    private final LogProperties props;
    private final RestTemplate restTemplate;
    private final FileLogSink fallback;
    private final ThreadPoolExecutor executor;

    public RemoteLogSink(LogProperties props, LoadBalancerClient loadBalancerClient, FileLogSink fallback) {
        this.props = props;
        this.fallback = fallback;
        this.restTemplate = buildRestTemplate(props, loadBalancerClient);
        AtomicInteger seq = new AtomicInteger();
        this.executor = new ThreadPoolExecutor(
                1, Math.max(1, props.getIngestMaxThreads()),
                60, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(Math.max(16, props.getIngestQueueCapacity())),
                r -> {
                    Thread t = new Thread(r, "oper-log-report-" + seq.incrementAndGet());
                    t.setDaemon(true);
                    return t;
                },
                // 队列打满时让调用线程自己跑：审计宁可变慢也不要静默丢
                new ThreadPoolExecutor.CallerRunsPolicy());
    }

    @Override
    public void submit(OperLogEntity record) {
        // 先落文件再上报：上报线程池可能拒绝任务，文件这一份是确定性的
        fallback.submit(record);
        executor.execute(() -> post(props.getIngestPath() + "/oper", record));
    }

    @Override
    public void submit(LoginLogEntity record) {
        fallback.submit(record);
        executor.execute(() -> post(props.getIngestPath() + "/login", record));
    }

    private void post(String path, Object body) {
        String url = "http://" + props.getIngestServiceId() + path;
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            // 用字符串做请求体，绕开 Boot 4 下 Jackson2 / Jackson3 两套转换器并存的选型问题
            headers.set(INGEST_TOKEN_HEADER, props.getIngestToken() == null ? "" : props.getIngestToken());
            restTemplate.postForEntity(url, new HttpEntity<>(AuditJson.write(body), headers), String.class);
        } catch (Exception e) {
            // 上报失败已经由 audit.log 兜住，这里只留一条线索，不重复刷错误
            log.warn("审计上报失败 url={}：{}", url, e.getMessage());
        }
    }

    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
    }

    private static RestTemplate buildRestTemplate(LogProperties props, LoadBalancerClient loadBalancerClient) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(props.getConnectTimeoutMs());
        factory.setReadTimeout(props.getReadTimeoutMs());
        RestTemplate template = new RestTemplate(factory);
        if (loadBalancerClient != null) {
            // 让 http://system-service 这种服务名地址能被解析；解析不了就直接失败并退回文件
            template.getInterceptors().add(new LoadBalancerInterceptor(loadBalancerClient));
        }
        return template;
    }
}
