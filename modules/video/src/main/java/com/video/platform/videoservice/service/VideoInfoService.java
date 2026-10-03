package com.video.platform.videoservice.service;

import com.video.platform.videoservice.entity.VideoInfo;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import org.springframework.web.multipart.MultipartFile;

public interface VideoInfoService extends IService<VideoInfo> {

    /**
     * 分页查询已发布视频（支持分类/关键词过滤）
     */
    IPage<VideoInfo> listVideos(long current, long size, Long categoryId, String keyword);

    /** 当前用户自己的投稿，包含待转码和已发布视频。 */
    IPage<VideoInfo> listMyVideos(Long userId, long current, long size);

    /**
     * 创建视频上传记录，返回 videoKey
     */
    VideoInfo createUpload(VideoInfo video);

    /**
     * 获取视频播放地址（HLS）
     */
    String getPlayUrl(Long id);

    /**
     * 真实上传：落存储（MinIO/本地）→ ffprobe 探测元信息 → 落库（状态=待转码）。
     * 转码由 TranscodeService 异步完成，完成后置为已发布。
     */
    VideoInfo upload(MultipartFile file, String title, String description, Long categoryId, Long userId);

    /**
     * 获取下载地址（MinIO 为预签名 URL）
     */
    String downloadUrl(Long id);
}
