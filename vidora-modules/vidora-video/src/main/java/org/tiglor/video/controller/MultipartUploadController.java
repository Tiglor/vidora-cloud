package org.tiglor.video.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.tiglor.common.core.ApiResult;
import org.tiglor.common.core.security.UserContext;
import org.tiglor.video.config.TranscodeProperties;
import org.tiglor.video.dto.MultipartCompleteRequest;
import org.tiglor.video.dto.MultipartInitRequest;
import org.tiglor.video.dto.MultipartInitResult;
import org.tiglor.video.dto.MultipartProgress;
import org.tiglor.video.entity.VideoInfo;
import org.tiglor.video.service.MultipartUploadService;
import org.tiglor.video.service.TranscodeService;

/**
 * 分片上传（断点续传 + MD5 秒传）。
 * <p>
 * 前端用法：算出整文件 MD5 → {@code POST /init} → 跳过返回的 uploadedIndexes，
 * 其余分片并发 {@code POST /chunk} → {@code POST /complete} → 轮询
 * {@code GET /videos/{id}/transcode-task} 等转码。
 * </p>
 */
@RestController
@RequestMapping("/videos/multipart")
@RequiredArgsConstructor
public class MultipartUploadController {

    private final MultipartUploadService multipartUploadService;
    private final TranscodeService transcodeService;
    private final TranscodeProperties transcodeProperties;

    /**
     * 初始化上传会话。{@code instant=true} 表示秒传命中，无需再传分片，直接调 complete。
     */
    @PostMapping("/init")
    @PreAuthorize("hasAuthority('video:upload')")
    public ApiResult<MultipartInitResult> init(@Valid @RequestBody MultipartInitRequest request) {
        return ApiResult.ok(multipartUploadService.init(request, UserContext.getUserId()));
    }

    /** 上传单个分片。同一下标可重复上传，服务端覆盖写并幂等计数 */
    @PostMapping(value = "/chunk", consumes = "multipart/form-data")
    @PreAuthorize("hasAuthority('video:upload')")
    public ApiResult<Void> chunk(@RequestParam String uploadId,
                                 @RequestParam int chunkIndex,
                                 @RequestPart("file") MultipartFile file) {
        multipartUploadService.uploadChunk(uploadId, chunkIndex, file, UserContext.getUserId());
        return ApiResult.ok();
    }

    /** 查询进度：返回服务端实测已收到的分片下标，刷新页面或换设备后靠它接着传 */
    @GetMapping("/progress")
    public ApiResult<MultipartProgress> progress(@RequestParam String uploadId) {
        return ApiResult.ok(multipartUploadService.progress(uploadId, UserContext.getUserId()));
    }

    /** 合并分片并生成视频记录。若开启转码则同时提交转码任务 */
    @PostMapping("/complete")
    @PreAuthorize("hasAuthority('video:upload')")
    public ApiResult<VideoInfo> complete(@Valid @RequestBody MultipartCompleteRequest request) {
        VideoInfo video = multipartUploadService.complete(request, UserContext.getUserId());
        if (transcodeProperties.isEnabled()) {
            transcodeService.submit(video.getId());
        }
        return ApiResult.ok(video);
    }
}
