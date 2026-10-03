package com.video.platform.videoservice.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.video.platform.common.ApiResult;
import com.video.platform.common.security.UserContext;
import com.video.platform.videoservice.config.TranscodeProperties;
import com.video.platform.videoservice.client.SystemUserClient;
import com.video.platform.videoservice.client.dto.RemoteUserDTO;
import com.video.platform.videoservice.entity.TranscodeTask;
import com.video.platform.videoservice.entity.VideoInfo;
import com.video.platform.videoservice.service.TranscodeService;
import com.video.platform.videoservice.service.VideoInfoService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/videos")
@RequiredArgsConstructor
public class VideoController {

    private final VideoInfoService videoInfoService;
    private final TranscodeService transcodeService;
    private final TranscodeProperties transcodeProperties;
    private final SystemUserClient systemUserClient;

    @GetMapping("/page")
    public ApiResult<IPage<VideoInfo>> page(@RequestParam(defaultValue = "1") long current,
                                            @RequestParam(defaultValue = "10") long size,
                                            @RequestParam(required = false) Long categoryId,
                                            @RequestParam(required = false) String keyword) {
        return ApiResult.ok(videoInfoService.listVideos(current, size, categoryId, keyword));
    }

    @GetMapping("/mine")
    public ApiResult<IPage<VideoInfo>> mine(@RequestParam(defaultValue = "1") long current,
                                            @RequestParam(defaultValue = "10") long size) {
        return ApiResult.ok(videoInfoService.listMyVideos(UserContext.getUserId(), current, size));
    }

    @GetMapping("/{id}")
    public ApiResult<VideoInfo> detail(@PathVariable Long id) {
        return ApiResult.ok(videoInfoService.getById(id));
    }

    /**
     * 跨服务调用示例：视频服务通过 OpenFeign 查询系统服务中的投稿用户。
     * 该接口用于验证 Nacos 服务发现、Feign 负载均衡和身份头透传链路。
     */
    @GetMapping("/{id}/owner")
    public ApiResult<RemoteUserDTO> owner(@PathVariable Long id) {
        VideoInfo video = videoInfoService.getById(id);
        if (video == null || video.getUserId() == null) {
            return ApiResult.error(404, "视频或投稿用户不存在");
        }
        return systemUserClient.getById(video.getUserId());
    }

    /**
     * 上传视频：落存储 + ffprobe 探测元信息。若启用转码，则自动提交异步转码任务。
     * 前端应轮询 {@code GET /{id}/transcode-task} 获取转码状态，成功后即可播放。
     */
    @PostMapping(value = "/upload", consumes = "multipart/form-data")
    @PreAuthorize("hasAuthority('video:upload')")
    public ApiResult<VideoInfo> upload(@RequestPart("file") MultipartFile file,
                                       @RequestParam(required = false) String title,
                                       @RequestParam(required = false) String description,
                                       @RequestParam(required = false) Long categoryId) {
        Long userId = UserContext.getUserId();
        VideoInfo v = videoInfoService.upload(file, title, description, categoryId, userId);
        if (transcodeProperties.isEnabled()) {
            transcodeService.submit(v.getId());
        }
        return ApiResult.ok(v);
    }

    /**
     * 提交转码任务（需 video:transcode 权限）。返回任务 ID，异步执行。
     */
    @PostMapping("/{id}/transcode")
    @PreAuthorize("hasAuthority('video:transcode')")
    public ApiResult<String> transcode(@PathVariable Long id) {
        return ApiResult.ok(transcodeService.submit(id).getId().toString());
    }

    /**
     * 查询某视频最新转码任务的状态与进度（供前端轮询）。
     * 状态：0-待处理 1-处理中 2-成功 3-失败
     */
    @GetMapping("/{id}/transcode-task")
    public ApiResult<TranscodeTask> transcodeTask(@PathVariable Long id) {
        return ApiResult.ok(transcodeService.latest(id));
    }

    /** 获取下载地址（MinIO 为限时预签名 URL） */
    @GetMapping("/{id}/download")
    public ApiResult<String> downloadUrl(@PathVariable Long id) {
        return ApiResult.ok(videoInfoService.downloadUrl(id));
    }

    /** 播放地址：已转码返回 HLS(m3u8) 公共地址，否则返回源文件下载地址 */
    @GetMapping("/{id}/play-url")
    public ApiResult<String> playUrl(@PathVariable Long id) {
        return ApiResult.ok(videoInfoService.getPlayUrl(id));
    }
}
