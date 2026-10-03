package org.tiglor.video.entity;

import org.tiglor.common.core.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("video_info")
public class VideoInfo extends BaseEntity {

    private String videoKey;
    private Long userId;
    private String title;
    private String description;
    private String coverUrl;
    private Integer duration;
    private Integer width;
    private Integer height;
    private Long fileSize;
    private String fileHash;
    /** 存储对象名（MinIO objectName 或本地相对路径） */
    private String storagePath;
    /** 转码后的 HLS 播放索引（m3u8）地址 */
    private String hlsUrl;
    private Integer status;
    private Integer visibility;
    private Long categoryId;
    private Integer playCount;
    private Integer likeCount;
    private Integer commentCount;
    private Integer shareCount;
    private LocalDateTime publishTime;
}
