package org.tiglor.video.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import java.util.List;
import java.util.Objects;
import org.tiglor.api.system.dto.RemoteUserDTO;
import org.tiglor.common.core.ApiResult;
import org.tiglor.common.log.annotation.BusinessType;
import org.tiglor.common.log.annotation.OperLog;
import org.tiglor.common.core.BizException;
import org.tiglor.common.core.ResultCode;
import org.tiglor.common.core.security.UserContext;
import org.tiglor.video.config.TranscodeProperties;
import org.tiglor.video.client.SystemUserClient;
import org.tiglor.video.entity.TranscodeTask;
import org.tiglor.video.entity.VideoInfo;
import org.tiglor.video.service.TranscodeService;
import org.tiglor.video.service.VideoInfoService;
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

    /**
     * 视频详情。不存在（或 id 写错）时报 404，而不是 {@code code:200 + data:null}：
     * 前端拿到的类型是非空实体，null 会让详情页渲染函数直接抛 TypeError，
     * 监控里这也是一次「成功」，永远发现不了。
     */
    @GetMapping("/{id}")
    public ApiResult<VideoInfo> detail(@PathVariable Long id) {
        VideoInfo video = videoInfoService.getById(id);
        if (video == null) {
            throw new BizException(ResultCode.NOT_FOUND, "视频不存在");
        }
        return ApiResult.ok(video);
    }

    /**
     * 按 id 批量取视频，供调用方补全展示字段（如「我的收藏」列表：
     * interact 服务只存 targetId，视频标题封面在 video 库里）。
     *
     * @return 只含存在的 id，顺序不保证；已删除的视频静默跳过，调用方按 id 自行对齐
     */
    @PostMapping("/batch")
    public ApiResult<List<VideoInfo>> batch(@RequestBody List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return ApiResult.ok(List.of());
        }
        // 上限夹住：一次列表页最多 20 条，给 50 足够，再多就是被人拿来拖全库了
        List<Long> limited = ids.stream().filter(Objects::nonNull).distinct().limit(50).toList();
        return ApiResult.ok(videoInfoService.listByIds(limited));
    }

    /**
     * 跨服务调用示例：视频服务通过 OpenFeign 查询系统服务中的投稿用户。
     * 该接口用于验证 Nacos 服务发现、Feign 负载均衡和身份头透传链路。
     */
    @GetMapping("/{id}/owner")
    public ApiResult<RemoteUserDTO> owner(@PathVariable Long id) {
        VideoInfo video = videoInfoService.getById(id);
        if (video == null || video.getUserId() == null) {
            throw new BizException(ResultCode.NOT_FOUND, "视频或投稿用户不存在");
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
    @OperLog(title = "视频管理", type = BusinessType.UPDATE)
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
