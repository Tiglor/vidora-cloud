package com.video.platform.videoservice.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.video.platform.common.BizException;
import com.video.platform.common.ResultCode;
import com.video.platform.videoservice.entity.VideoInfo;
import com.video.platform.videoservice.mapper.VideoInfoMapper;
import com.video.platform.videoservice.service.MediaInfo;
import com.video.platform.videoservice.service.StorageService;
import com.video.platform.videoservice.service.VideoInfoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class VideoInfoServiceImpl extends ServiceImpl<VideoInfoMapper, VideoInfo> implements VideoInfoService {

    /** 视频状态：0-上传完成待转码 3-已发布 */
    private static final int STATUS_PUBLISHED = 3;
    private static final int STATUS_UPLOADED = 0;

    private final StorageService storageService;
    private final FfmpegService ffmpegService;

    @Override
    public IPage<VideoInfo> listVideos(long current, long size, Long categoryId, String keyword) {
        LambdaQueryWrapper<VideoInfo> qw = new LambdaQueryWrapper<>();
        qw.eq(categoryId != null, VideoInfo::getCategoryId, categoryId)
          .like(StringUtils.isNotBlank(keyword), VideoInfo::getTitle, keyword)
          .eq(VideoInfo::getStatus, STATUS_PUBLISHED)
          .orderByDesc(VideoInfo::getPublishTime);
        return page(new Page<>(current, size), qw);
    }

    @Override
    public IPage<VideoInfo> listMyVideos(Long userId, long current, long size) {
        if (userId == null) {
            throw new BizException(ResultCode.UNAUTHORIZED, "未登录");
        }
        LambdaQueryWrapper<VideoInfo> qw = new LambdaQueryWrapper<>();
        qw.eq(VideoInfo::getUserId, userId)
                .orderByDesc(VideoInfo::getCreateTime);
        return page(new Page<>(current, size), qw);
    }

    @Override
    public VideoInfo createUpload(VideoInfo video) {
        video.setVideoKey(UUID.randomUUID().toString().replace("-", ""));
        video.setStatus(STATUS_UPLOADED);
        video.setPlayCount(0);
        video.setLikeCount(0);
        video.setCommentCount(0);
        video.setShareCount(0);
        save(video);
        return video;
    }

    @Override
    public VideoInfo upload(MultipartFile file, String title, String description, Long categoryId, Long userId) {
        if (file == null || file.isEmpty()) {
            throw new BizException(ResultCode.VALIDATE_FAILED, "上传文件为空");
        }
        String videoKey = UUID.randomUUID().toString().replace("-", "");
        String ext = extensionOf(file.getOriginalFilename());
        String objectName = "video/" + LocalDate.now() + "/" + videoKey + (ext.isEmpty() ? "" : "." + ext);

        // 1. 落存储
        try (InputStream in = file.getInputStream()) {
            storageService.upload(objectName, in, file.getContentType(), file.getSize());
        } catch (IOException e) {
            throw new BizException(ResultCode.FAIL, "读取上传文件失败：" + e.getMessage());
        }

        // 2. 落库
        VideoInfo v = new VideoInfo();
        v.setVideoKey(videoKey);
        v.setUserId(userId);
        v.setTitle(title == null || title.isBlank() ? defaultTitle(file.getOriginalFilename()) : title);
        v.setDescription(description);
        v.setCategoryId(categoryId);
        v.setFileSize(file.getSize());
        v.setStoragePath(objectName);
        v.setStatus(STATUS_UPLOADED);
        v.setPlayCount(0);
        v.setLikeCount(0);
        v.setCommentCount(0);
        v.setShareCount(0);

        // 3. ffprobe 探测元信息（未安装 ffprobe 时降级，不影响上传）
        try {
            Path local = storageService.fetchToTempFile(objectName);
            MediaInfo info = ffmpegService.probe(local);
            v.setDuration(info.getDurationSec());
            v.setWidth(info.getWidth());
            v.setHeight(info.getHeight());
        } catch (Exception e) {
            log.warn("媒体信息探测失败（ffprobe 未安装或不可用），跳过。videoKey={}", videoKey, e);
        }

        save(v);
        return v;
    }

    @Override
    public String downloadUrl(Long id) {
        VideoInfo v = getById(id);
        if (v == null || v.getStoragePath() == null) {
            throw new BizException(ResultCode.NOT_FOUND, "视频不存在或未上传文件");
        }
        return storageService.downloadUrl(v.getStoragePath());
    }

    @Override
    public String getPlayUrl(Long id) {
        VideoInfo video = getById(id);
        if (video == null) {
            throw new BizException(ResultCode.NOT_FOUND, "视频不存在");
        }
        // 已转码则返回 HLS 地址，否则回退到源文件的下载地址
        if (StringUtils.isNotBlank(video.getHlsUrl())) {
            return video.getHlsUrl();
        }
        if (StringUtils.isNotBlank(video.getStoragePath())) {
            return storageService.downloadUrl(video.getStoragePath());
        }
        return "https://cdn.example.com/" + video.getVideoKey() + "/index.m3u8";
    }

    private static String extensionOf(String filename) {
        if (filename == null) {
            return "";
        }
        int idx = filename.lastIndexOf('.');
        return idx < 0 ? "" : filename.substring(idx + 1);
    }

    private static String defaultTitle(String filename) {
        return (filename == null || filename.isBlank()) ? "未命名视频" : filename;
    }
}
