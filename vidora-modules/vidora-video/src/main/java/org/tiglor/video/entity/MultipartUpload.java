package org.tiglor.video.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 分片上传会话（断点续传 + MD5 秒传）。
 * <p>
 * 不继承 {@code BaseEntity}：video_multipart_upload 表没有 is_deleted 列，
 * 会话是短生命周期数据，靠状态机流转而不是逻辑删除。
 * </p>
 */
@Data
@TableName("video_multipart_upload")
public class MultipartUpload implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 对外暴露的上传任务 ID（UUID），接口只认它，不暴露自增主键 */
    private String uploadId;
    /** 发起上传的用户，所有后续操作都要校验归属 */
    private Long userId;
    /** 合并完成后关联的 video_info.id */
    private Long videoId;

    private String fileName;
    /** 整文件 MD5，秒传与断点续传都按它检索 */
    private String fileHash;
    private Long fileSize;
    private Integer chunkSize;
    private Integer totalChunks;
    /** 已上传分片数。并发写同一分片时可能偏大，只作进度提示，完整性校验以对象存储的分片清单为准 */
    private Integer completedChunks;

    private String bucket;
    /** 合并后目标对象名 */
    private String objectKey;

    /** 状态：0-上传中 1-分片已收齐 2-已合并 */
    private Integer status;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
