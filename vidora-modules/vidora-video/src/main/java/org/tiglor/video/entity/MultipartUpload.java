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

    /** 自增主键，只在库内串联用；接口一律认 {@link #uploadId} */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 对外暴露的上传任务 ID（UUID），接口只认它，不暴露自增主键 */
    private String uploadId;
    /** 发起上传的用户，所有后续操作都要校验归属 */
    private Long userId;
    /** 合并完成后关联的 video_info.id */
    private Long videoId;

    /** 用户当时的原始文件名，超长会被截断。complete 没传标题时就用它兜底 */
    private String fileName;
    /** 整文件 MD5，秒传与断点续传都按它检索 */
    private String fileHash;
    /** 整文件字节数。与会话里登记的 chunkSize 一起决定分片下标越界和最后一片该有多大 */
    private Long fileSize;
    /**
     * 前端切片大小（字节）。除最后一片外每片都必须正好等于它，否则服务端按字节数拒收。
     * <p>它与 fileSize 共同构成续传指纹：历史会话这两个值对不上就作废重传，而不是报错。</p>
     */
    private Integer chunkSize;
    /** 总分片数，由 fileSize 除以 chunkSize 向上取整得出；S3 单次 compose 上限 10000 片 */
    private Integer totalChunks;
    /** 已上传分片数。并发写同一分片时可能偏大，只作进度提示，完整性校验以对象存储的分片清单为准 */
    private Integer completedChunks;

    /** 建档那一刻从配置里读到的 bucket。读写对象时用的都是当前配置值而不是这一列，所以换了 bucket 后历史行的这个值只是留档 */
    private String bucket;
    /** 合并后目标对象名 */
    private String objectKey;

    /** 状态：0-上传中 1-分片已收齐 2-已合并 */
    private Integer status;

    /** 会话建立时间，由建表默认值填；本实体不继承 BaseEntity，这两个时间都不走自动填充 */
    private LocalDateTime createTime;
    /** 会话最后变动时间，由建表的 ON UPDATE 自己往前推，代码里没有赋值 */
    private LocalDateTime updateTime;
}
