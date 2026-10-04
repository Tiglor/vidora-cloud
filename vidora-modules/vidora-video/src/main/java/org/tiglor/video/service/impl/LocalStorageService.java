package org.tiglor.video.service.impl;

import org.tiglor.common.core.BizException;
import org.tiglor.common.core.ResultCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.tiglor.video.config.StorageProperties;
import org.tiglor.video.service.StorageService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.stream.Stream;

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

    @Override
    public boolean exists(String objectName) {
        return Files.isRegularFile(resolve(objectName));
    }

    @Override
    public List<String> listObjectNames(String prefix) {
        Path root = Paths.get(properties.getLocalDir());
        Path base = root.resolve(prefix).normalize();
        // prefix 指向的分片目录在第一个分片落地前还不存在，退到最近的存在目录再遍历
        Path searchRoot = Files.isDirectory(base) ? base : base.getParent();
        if (searchRoot == null || !Files.isDirectory(searchRoot)) {
            return List.of();
        }
        String normalized = prefix.replace('\\', '/');
        try (Stream<Path> walk = Files.walk(searchRoot)) {
            return walk.filter(Files::isRegularFile)
                    .map(file -> root.relativize(file).toString().replace('\\', '/'))
                    .filter(name -> name.startsWith(normalized))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            log.error("遍历本地分片目录失败, prefix={}", prefix, e);
            throw new BizException(ResultCode.FAIL, "列举已上传分片失败：" + e.getMessage());
        }
    }

    @Override
    public void compose(String targetObjectName, List<String> sourceObjectNames) {
        if (sourceObjectNames == null || sourceObjectNames.isEmpty()) {
            throw new BizException(ResultCode.VALIDATE_FAILED, "没有可合并的分片");
        }
        Path target = resolve(targetObjectName);
        // 先拼到同目录的临时文件再 rename：直接写目标的话，中途缺一片就会在 objectKey 上
        // 留下半个损坏文件，而它的路径正是后续 VideoInfo.storagePath 要用的那个
        Path staging;
        try {
            Files.createDirectories(target.getParent());
            staging = Files.createTempFile(target.getParent(), ".merging-", ".part");
        } catch (IOException e) {
            throw new BizException(ResultCode.FAIL, "创建合并临时文件失败：" + e.getMessage());
        }
        try {
            try (OutputStream out = Files.newOutputStream(staging)) {
                for (String name : sourceObjectNames) {
                    Path part = resolve(name);
                    if (!Files.isRegularFile(part)) {
                        throw new BizException(ResultCode.NOT_FOUND, "分片文件不存在：" + name);
                    }
                    Files.copy(part, out);
                }
            }
            Files.move(staging, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (BizException e) {
            deleteQuietly(staging);
            throw e;
        } catch (Exception e) {
            deleteQuietly(staging);
            log.error("本地合并分片失败, target={}", targetObjectName, e);
            throw new BizException(ResultCode.FAIL, "合并分片失败：" + e.getMessage());
        }
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // 临时文件残留不影响正确性
        }
    }
}
