package org.tiglor.video.service;

import org.tiglor.common.core.BizException;
import org.tiglor.common.core.ResultCode;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * 视频文件存储抽象：屏蔽 MinIO 与本地磁盘差异。
 * <p>
 * 新增 {@link #publicUrl(String)} 与 {@link #uploadDir(Path, String)} 以支持转码产物（HLS 切片）回传对象存储
 * 并对外暴露可被 CDN 回源的公共地址——这是“对象存储 + CDN”播放分发的标准做法。
 * </p>
 */
public interface StorageService {

    /** 上传：objectName 为存储对象名（含目录前缀） */
    String upload(String objectName, InputStream in, String contentType, long size);

    /** 获取下载地址（MinIO 返回预签名 URL；本地返回相对路径） */
    String downloadUrl(String objectName);

    /** 获取公共播放地址（无签名，可被 HLS 分片 / CDN 直接引用）。生产建议 Bucket 公开读或经 CDN 回源 */
    String publicUrl(String objectName);

    /** 删除 */
    void delete(String objectName);

    /** 读取为本地临时文件（转码前需要落到磁盘） */
    Path fetchToTempFile(String objectName);

    /**
     * 批量上传目录（递归）到指定前缀，保留相对路径结构。
     * 用于把 ffmpeg 生成的 HLS 切片目录整体回传对象存储。
     */
    default void uploadDir(Path dir, String prefix) {
        if (dir == null || !Files.exists(dir) || !Files.isDirectory(dir)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.filter(Files::isRegularFile).forEach(file -> {
                String rel = dir.relativize(file).toString().replace('\\', '/');
                String objectName = prefix + "/" + rel;
                try (InputStream in = Files.newInputStream(file)) {
                    upload(objectName, in, probeContentType(file), Files.size(file));
                } catch (IOException e) {
                    throw new BizException(ResultCode.FAIL,
                            "批量上传失败：" + objectName + " " + e.getMessage());
                }
            });
        } catch (IOException e) {
            throw new BizException(ResultCode.FAIL, "遍历目录失败：" + dir + " " + e.getMessage());
        }
    }

    /** 推测文件 Content-Type（上传时使用） */
    default String probeContentType(Path file) {
        try {
            String t = Files.probeContentType(file);
            return t == null ? "application/octet-stream" : t;
        } catch (IOException e) {
            return "application/octet-stream";
        }
    }
}
