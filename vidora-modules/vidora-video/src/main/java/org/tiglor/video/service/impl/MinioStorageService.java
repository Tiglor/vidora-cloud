package org.tiglor.video.service.impl;

import org.tiglor.common.core.BizException;
import org.tiglor.common.core.ResultCode;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.tiglor.video.config.StorageProperties;
import org.tiglor.video.service.StorageService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * MinIO（S3 兼容）对象存储实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "storage.type", havingValue = "minio", matchIfMissing = true)
public class MinioStorageService implements StorageService {

    private final StorageProperties properties;
    private volatile MinioClient client;
    private volatile boolean bucketReady;

    private MinioClient client() {
        if (client == null) {
            synchronized (this) {
                if (client == null) {
                    client = MinioClient.builder()
                            .endpoint(properties.getEndpoint())
                            .credentials(properties.getAccessKey(), properties.getSecretKey())
                            .build();
                }
            }
        }
        return client;
    }

    /**
     * 开发环境和空 MinIO 实例首次启动时自动创建 bucket，避免第一次上传才暴露
     * NoSuchBucket。生产环境仍建议通过部署脚本预创建并配置访问策略。
     */
    private void ensureBucket() {
        if (bucketReady) {
            return;
        }
        synchronized (this) {
            if (bucketReady) {
                return;
            }
            try {
                MinioClient minio = client();
                boolean exists = minio.bucketExists(BucketExistsArgs.builder()
                        .bucket(properties.getBucket())
                        .build());
                if (!exists) {
                    minio.makeBucket(MakeBucketArgs.builder()
                            .bucket(properties.getBucket())
                            .build());
                    log.info("已创建 MinIO bucket: {}", properties.getBucket());
                }
                bucketReady = true;
            } catch (Exception e) {
                throw new BizException(ResultCode.FAIL, "连接对象存储或初始化 bucket 失败：" + e.getMessage());
            }
        }
    }

    @Override
    public String upload(String objectName, InputStream in, String contentType, long size) {
        try {
            ensureBucket();
            client().putObject(PutObjectArgs.builder()
                    .bucket(properties.getBucket())
                    .object(objectName)
                    .stream(in, size, -1)
                    .contentType(contentType == null ? "application/octet-stream" : contentType)
                    .build());
            return objectName;
        } catch (Exception e) {
            log.error("上传 MinIO 失败, objectName={}", objectName, e);
            throw new BizException(ResultCode.FAIL, "文件上传到对象存储失败：" + e.getMessage());
        }
    }

    @Override
    public String downloadUrl(String objectName) {
        try {
            return client().getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(properties.getBucket())
                    .object(objectName)
                    .expiry(properties.getUrlExpireSeconds())
                    .build());
        } catch (Exception e) {
            log.error("生成下载地址失败, objectName={}", objectName, e);
            throw new BizException(ResultCode.FAIL, "生成下载地址失败：" + e.getMessage());
        }
    }

    @Override
    public String publicUrl(String objectName) {
        // 生产建议：Bucket 设为公开读或经 CDN 回源，避免预签名 URL 无法被 HLS 分片继承签名
        String endpoint = properties.getEndpoint();
        if (endpoint.endsWith("/")) {
            endpoint = endpoint.substring(0, endpoint.length() - 1);
        }
        return endpoint + "/" + properties.getBucket() + "/" + objectName;
    }

    @Override
    public void delete(String objectName) {
        try {
            client().removeObject(RemoveObjectArgs.builder()
                    .bucket(properties.getBucket())
                    .object(objectName)
                    .build());
        } catch (Exception e) {
            log.warn("删除对象失败, objectName={}", objectName, e);
        }
    }

    @Override
    public Path fetchToTempFile(String objectName) {
        try (InputStream in = client().getObject(io.minio.GetObjectArgs.builder()
                .bucket(properties.getBucket())
                .object(objectName)
                .build())) {
            Path tmp = Files.createTempFile("video-", ".bin");
            Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
            return tmp;
        } catch (Exception e) {
            log.error("拉取对象失败, objectName={}", objectName, e);
            throw new BizException(ResultCode.FAIL, "读取视频文件失败：" + e.getMessage());
        }
    }
}
