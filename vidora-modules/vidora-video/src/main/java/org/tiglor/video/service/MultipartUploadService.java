package org.tiglor.video.service;

import org.tiglor.video.dto.MultipartCompleteRequest;
import org.tiglor.video.dto.MultipartInitRequest;
import org.tiglor.video.dto.MultipartInitResult;
import org.tiglor.video.dto.MultipartProgress;
import org.tiglor.video.entity.VideoInfo;
import org.springframework.web.multipart.MultipartFile;

/**
 * 分片上传：断点续传 + MD5 秒传。
 * <p>
 * 流程：{@link #init} 换 uploadId 与已收分片清单 → 前端并发调 {@link #uploadChunk} 传缺失分片
 * → {@link #complete} 服务端合并并生成视频记录 → 由调用方决定是否提交转码。
 * </p>
 * <p>
 * 分片状态以对象存储的实测清单为准，库里的 completed_chunks 只作进度提示：
 * 客户端超时重传同一分片、或多个分片并发到达时，计数可能短暂与实际不符。
 * </p>
 */
public interface MultipartUploadService {

    /**
     * 初始化上传会话，一次调用对应一次上传尝试。
     * 命中秒传时 {@code instant=true}，前端可直接调 {@link #complete}；
     * 命中断点续传时返回同一个 uploadId 和服务端已确认收到的分片下标；
     * 其余情况（包括同一文件再投一次）都会得到「全新的 uploadId」。
     */
    MultipartInitResult init(MultipartInitRequest request, Long userId);

    /** 上传单个分片。可并发、可重复上传同一下标（覆盖写，幂等） */
    void uploadChunk(String uploadId, int chunkIndex, MultipartFile file, Long userId);

    /** 查询进度：返回服务端实测已收到的分片下标 */
    MultipartProgress progress(String uploadId, Long userId);

    /**
     * 合并分片并生成视频记录（状态=待转码）。重复调用返回同一个视频，不会重复建记录。
     * 不提交转码——由调用方按 transcode.enabled 决定，与普通上传接口保持一致。
     */
    VideoInfo complete(MultipartCompleteRequest request, Long userId);
}
