package com.video.platform.gatewayservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;


/**
 * 网关服务启动类
 */
@SpringBootApplication(scanBasePackages = "com.video.platform.gatewayservice")

public class GatewayServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayServiceApplication.class, args);
    }
}
