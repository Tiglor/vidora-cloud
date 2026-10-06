package org.tiglor.video.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 分片上传初始化请求。前端在选完文件后先算整文件 MD5，用它换取 uploadId 与已上传分片清单。
 */
@Data
public class MultipartInitRequest {

    /**
     * MinIO/S3 的 composeObject 要求除最后一片外每片不小于 5 MiB，
     * 小于这个值的分片在合并阶段会被服务端直接拒绝。
     */
    public static final long MIN_CHUNK_SIZE = 5L * 1024 * 1024;

    /** S3 单次 compose 最多 10000 个源分片；配合 5 MiB 下限，单文件上限约 50 GiB */
    public static final int MAX_CHUNKS = 10_000;

    /**
     * 原始文件名，只用作标题兜底和对象名后缀，不参与路径解析。
     * <p>超过 200 字符服务端截断；后缀必须是纯字母数字才保留，否则拼出来的对象名不带扩展名。</p>
     */
    @NotBlank(message = "文件名不能为空")
    @Size(max = 200, message = "文件名过长")
    private String fileName;

    /** 整文件 MD5（也接受 SHA-256），秒传与断点续传都按它匹配 */
    @NotBlank(message = "文件哈希不能为空")
    @Pattern(regexp = "^[A-Fa-f0-9]{32,64}$", message = "文件哈希必须是 32 位 MD5 或 64 位 SHA-256 十六进制串")
    private String fileHash;

    /** 整文件字节数，必须与实际切片后的总和对得上，否则最后一片会因大小不符被拒收 */
    @NotNull(message = "文件大小不能为空")
    @Positive(message = "文件大小必须大于 0")
    private Long fileSize;

    /** 前端切片大小，必须 >= 5 MiB 且能整除 fileSize 之外允许最后一片不足 */
    @NotNull(message = "分片大小不能为空")
    @Min(value = MIN_CHUNK_SIZE, message = "分片大小不能小于 5MB")
    @Max(value = 100L * 1024 * 1024, message = "分片大小不能超过 100MB")
    private Integer chunkSize;
}
