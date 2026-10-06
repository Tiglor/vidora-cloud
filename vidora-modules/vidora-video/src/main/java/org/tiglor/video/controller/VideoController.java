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

/**
 * 视频对外接口：列表/详情/批量取，加上投稿侧的上传、转码与播放下载地址。
 * <p>
 * 读侧只有「已发布」一种口径，管理端的视频列表目前也打在这里，所以它同样只看得到已发布的；
 * status 的写入路径当前止于 0（上传）→ 3（转码成功置 3 并回填 publish_time），没有审核与下架接口。
 * </p>
 */
@RestController
@RequestMapping("/videos")
@RequiredArgsConstructor
public class VideoController {

    private final VideoInfoService videoInfoService;
    private final TranscodeService transcodeService;
    private final TranscodeProperties transcodeProperties;
    private final SystemUserClient systemUserClient;

    /**
     * 视频分页
     *
     * <p>只给已发布的，按发布时间从新到旧。</p>
     * <p>
     * 无需登录，也没有任何按当前用户的差异化逻辑——status 不等于已发布的（待转码、转码中、转码失败）
     * 都不在这个列表里。所以首页翻到的顺序等于转码完成的先后，跟上传早晚不是一回事。
     * </p>
     *
     * @param keyword 标题模糊匹配（LIKE，不是分词搜索），空白等同于不过滤
     * @return MyBatis-Plus 的分页对象（records / total / pages），不是裸数组
     */
    @GetMapping("/page")
    public ApiResult<IPage<VideoInfo>> page(@RequestParam(defaultValue = "1") long current,
                                            @RequestParam(defaultValue = "10") long size,
                                            @RequestParam(required = false) Long categoryId,
                                            @RequestParam(required = false) String keyword) {
        return ApiResult.ok(videoInfoService.listVideos(current, size, categoryId, keyword));
    }

    /**
     * 我的投稿分页
     *
     * <p>当前登录用户自己的投稿，不分状态：待转码、转码中和已发布的都在里面。</p>
     * <p>
     * 与 {@code /page} 是两套口径：这里按创建时间倒序（发布过的也排在它上传的位置上），
     * 那边按发布时间倒序且只有已发布。未登录时报 401，userId 一律取自网关透传的身份，不接受前端传入。
     * </p>
     *
     * @return 分页对象，其中 storagePath、hlsUrl 等内部字段原样带出，不做脱敏裁剪
     */
    @GetMapping("/mine")
    public ApiResult<IPage<VideoInfo>> mine(@RequestParam(defaultValue = "1") long current,
                                            @RequestParam(defaultValue = "10") long size) {
        return ApiResult.ok(videoInfoService.listMyVideos(UserContext.getUserId(), current, size));
    }

    /**
     * 查询视频详情
     *
     * <p>
     * 不存在（或 id 写错）时报 404，而不是 {@code code:200 + data:null}：
     * 前端拿到的类型是非空实体，null 会让详情页渲染函数直接抛 TypeError，
     * 监控里这也是一次「成功」，永远发现不了。
     * </p>
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
     * 按 id 批量取视频
     *
     * <p>供调用方补全展示字段（如「我的收藏」列表：interact 服务只存 targetId，视频标题封面在 video 库里）。</p>
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
     * 查询视频投稿用户
     *
     * <p>
     * 跨服务调用示例：视频服务通过 OpenFeign 查询系统服务中的投稿用户。
     * 该接口用于验证 Nacos 服务发现、Feign 负载均衡和身份头透传链路。
     * </p>
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
     * 上传视频文件
     *
     * <p>
     * 落存储 + ffprobe 探测元信息。若启用转码，则自动提交异步转码任务。
     * 前端应轮询 {@code GET /{id}/transcode-task} 获取转码状态，成功后即可播放。
     * </p>
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
     * 提交转码任务
     *
     * <p>需 {@code video:transcode} 权限。返回任务 ID，异步执行。</p>
     */
    @PostMapping("/{id}/transcode")
    @PreAuthorize("hasAuthority('video:transcode')")
    @OperLog(title = "视频管理", type = BusinessType.UPDATE)
    public ApiResult<String> transcode(@PathVariable Long id) {
        return ApiResult.ok(transcodeService.submit(id).getId().toString());
    }

    /**
     * 查询最新转码任务
     *
     * <p>查询某视频最新转码任务的状态与进度（供前端轮询）。状态：0-待处理 1-处理中 2-成功 3-失败。</p>
     */
    @GetMapping("/{id}/transcode-task")
    public ApiResult<TranscodeTask> transcodeTask(@PathVariable Long id) {
        return ApiResult.ok(transcodeService.latest(id));
    }

    /**
     * 获取下载地址
     *
     * <p>MinIO 为限时预签名 URL。</p>
     */
    @GetMapping("/{id}/download")
    public ApiResult<String> downloadUrl(@PathVariable Long id) {
        return ApiResult.ok(videoInfoService.downloadUrl(id));
    }

    /**
     * 获取播放地址
     *
     * <p>已转码返回 HLS(m3u8) 公共地址，否则返回源文件下载地址。</p>
     */
    @GetMapping("/{id}/play-url")
    public ApiResult<String> playUrl(@PathVariable Long id) {
        return ApiResult.ok(videoInfoService.getPlayUrl(id));
    }
}
