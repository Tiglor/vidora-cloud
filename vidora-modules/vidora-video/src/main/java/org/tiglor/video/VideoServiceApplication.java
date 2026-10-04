package org.tiglor.video;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 视频服务启动类
 */
@SpringBootApplication(scanBasePackages = "org.tiglor")
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "org.tiglor.video.client")
public class VideoServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(VideoServiceApplication.class, args);
    }
}
