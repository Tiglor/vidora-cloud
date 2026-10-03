package com.video.platform.interactservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 互动服务启动类
 */
@SpringBootApplication(scanBasePackages = "com.video.platform")
@EnableDiscoveryClient
public class InteractServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(InteractServiceApplication.class, args);
    }
}
