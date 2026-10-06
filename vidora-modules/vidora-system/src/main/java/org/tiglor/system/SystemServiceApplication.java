package org.tiglor.system;

import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 系统权限服务启动类。
 * <p>
 * {@code @EnableDubbo} 不能省：{@code @DubboService} 不是 Spring 的刻板注解，
 * 上面那个 {@code scanBasePackages} 扫不到它，必须由 Dubbo 自己的扫描器认领后才会导出服务。
 */
@SpringBootApplication(scanBasePackages = "org.tiglor")
@EnableDiscoveryClient
@EnableDubbo(scanBasePackages = "org.tiglor.system.dubbo")
public class SystemServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(SystemServiceApplication.class, args);
    }
}
