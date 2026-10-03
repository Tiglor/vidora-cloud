package org.tiglor.video.service.impl;

import org.tiglor.common.core.BizException;
import org.tiglor.common.core.ResultCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.tiglor.video.config.StorageProperties;
import org.tiglor.video.service.StorageService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

/**
 * 本地磁盘存储实现（开发/测试用，无需 MinIO）
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "storage.type", havingValue = "local")
public class LocalStorageService implements StorageService {

    private final StorageProperties properties;

    private Path resolve(String objectName) {
        return Paths.get(properties.getLocalDir()).resolve(objectName).normalize();
    }

    @Override
    public String upload(String objectName, InputStream in, String contentType, long size) {
        try {
            Path target = resolve(objectName);
            Files.createDirectories(target.getParent());
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            return objectName;
        } catch (Exception e) {
            log.error("本地存储写入失败, objectName={}", objectName, e);
            throw new BizException(ResultCode.FAIL, "文件写入失败：" + e.getMessage());
        }
    }

    @Override
    public String downloadUrl(String objectName) {
        // 本地存储场景：返回相对路径，由网关/静态资源服务拼接
        return "/files/" + objectName;
    }

    @Override
    public String publicUrl(String objectName) {
        // 本地存储场景：返回静态资源基址 + 对象名（需配套静态资源服务或 Nginx 映射）
        String base = properties.getLocalPublicBase();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/" + objectName;
    }

    @Override
    public void delete(String objectName) {
        try {
            Files.deleteIfExists(resolve(objectName));
        } catch (Exception e) {
            log.warn("删除本地文件失败, objectName={}", objectName, e);
        }
    }

    @Override
    public Path fetchToTempFile(String objectName) {
        Path src = resolve(objectName);
        if (!Files.exists(src)) {
            throw new BizException(ResultCode.NOT_FOUND, "视频文件不存在：" + objectName);
        }
        return src;
    }
}
