package com.video.platform.videoservice.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 存储配置：type=minio（对象存储，默认）/ local（本地磁盘，便于无 MinIO 环境开发）
 */
@Data
@Component
@ConfigurationProperties(prefix = "storage")
public class StorageProperties {

    /** minio | local */
    private String type = "minio";

    /** 本地存储根目录（type=local 时使用） */
    private String localDir = "./upload";

    /** MinIO 服务地址，如 http://127.0.0.1:9000 */
    private String endpoint = "http://127.0.0.1:9000";
    private String accessKey = "minioadmin";
    private String secretKey = "minioadmin";
    private String bucket = "videos";

    /** 下载地址有效期（秒） */
    private int urlExpireSeconds = 3600;

    /** 本地存储场景的静态资源公共基址（publicUrl 使用），需配套静态资源服务或反向代理 */
    private String localPublicBase = "http://localhost:8102/files";
}
