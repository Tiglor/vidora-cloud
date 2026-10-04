package org.tiglor.video.service.impl;

import org.tiglor.common.core.BizException;
import org.tiglor.common.core.ResultCode;
import io.minio.ComposeObjectArgs;
import io.minio.ComposeSource;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.ListObjectsArgs;
import io.minio.MinioClient;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.RemoveObjectsArgs;
import io.minio.Result;
import io.minio.StatObjectArgs;
import io.minio.errors.ErrorResponseException;
import io.minio.http.Method;
import io.minio.messages.DeleteError;
import io.minio.messages.DeleteObject;
import io.minio.messages.Item;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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

    @Override
    public boolean exists(String objectName) {
        try {
            ensureBucket();
            client().statObject(StatObjectArgs.builder()
                    .bucket(properties.getBucket())
                    .object(objectName)
                    .build());
            return true;
        } catch (ErrorResponseException e) {
            // 只有明确的「不存在」才返回 false。连接失败也返回 false 的话，
            // 秒传会把「MinIO 挂了」当成「文件丢了」，用户被迫重传整个文件
            if (isNotFound(e)) {
                return false;
            }
            log.error("检查对象是否存在失败, objectName={}", objectName, e);
            throw new BizException(ResultCode.FAIL, "检查文件是否存在失败：" + e.getMessage());
        } catch (Exception e) {
            log.error("检查对象是否存在失败, objectName={}", objectName, e);
            throw new BizException(ResultCode.FAIL, "检查文件是否存在失败：" + e.getMessage());
        }
    }

    @Override
    public List<String> listObjectNames(String prefix) {
        try {
            ensureBucket();
            Iterable<Result<Item>> results = client().listObjects(ListObjectsArgs.builder()
                    .bucket(properties.getBucket())
                    .prefix(prefix)
                    .recursive(true)
                    .build());
            List<String> names = new ArrayList<>();
            for (Result<Item> result : results) {
                names.add(result.get().objectName());
            }
            Collections.sort(names);
            return names;
        } catch (Exception e) {
            log.error("列举对象失败, prefix={}", prefix, e);
            throw new BizException(ResultCode.FAIL, "列举已上传分片失败：" + e.getMessage());
        }
    }

    @Override
    public void compose(String targetObjectName, List<String> sourceObjectNames) {
        if (sourceObjectNames == null || sourceObjectNames.isEmpty()) {
            throw new BizException(ResultCode.VALIDATE_FAILED, "没有可合并的分片");
        }
        try {
            ensureBucket();
            List<ComposeSource> sources = sourceObjectNames.stream()
                    .map(name -> ComposeSource.builder()
                            .bucket(properties.getBucket())
                            .object(name)
                            .build())
                    .toList();
            // 服务端合并：分片数据不流经应用进程，否则上 GB 的文件会把服务打爆
            client().composeObject(ComposeObjectArgs.builder()
                    .bucket(properties.getBucket())
                    .object(targetObjectName)
                    .sources(sources)
                    .build());
        } catch (Exception e) {
            log.error("合并分片失败, target={}, parts={}", targetObjectName, sourceObjectNames.size(), e);
            throw new BizException(ResultCode.FAIL, "合并分片失败：" + e.getMessage());
        }
    }

    /**
     * 批量删除。合并后要清掉成百上千个分片对象，逐个 delete 就是同样多次 HTTP 往返；
     * removeObjects 的返回值是惰性的，必须遍历才会真正发请求。
     */
    @Override
    public void deleteAll(List<String> objectNames) {
        if (objectNames == null || objectNames.isEmpty()) {
            return;
        }
        try {
            List<DeleteObject> targets = objectNames.stream().map(DeleteObject::new).toList();
            Iterable<Result<DeleteError>> results = client().removeObjects(RemoveObjectsArgs.builder()
                    .bucket(properties.getBucket())
                    .objects(targets)
                    .build());
            for (Result<DeleteError> result : results) {
                DeleteError error = result.get();
                log.warn("删除分片对象失败, code={}, message={}", error.code(), error.message());
            }
        } catch (Exception e) {
            // 清理失败不影响已完成的合并，只留待人工或定时任务回收
            log.warn("批量删除对象失败, count={}", objectNames.size(), e);
        }
    }

    private static boolean isNotFound(ErrorResponseException e) {
        if (e.errorResponse() != null && "NoSuchKey".equals(e.errorResponse().code())) {
            return true;
        }
        return e.response() != null && e.response().code() == 404;
    }
}
