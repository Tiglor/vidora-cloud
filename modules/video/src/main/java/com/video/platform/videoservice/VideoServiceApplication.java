package com.video.platform.videoservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 视频服务启动类
 */
@SpringBootApplication(scanBasePackages = "com.video.platform")
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "com.video.platform.videoservice.client")
public class VideoServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(VideoServiceApplication.class, args);
    }
}
