package org.tiglor.video.dto;

import lombok.Data;

import java.util.List;

/**
 * 分片上传初始化结果。
 */
@Data
public class MultipartInitResult {

    /** 后续 {@code /chunk}、{@code /progress}、{@code /complete} 都要带上它；只能操作属于自己的会话 */
    private String uploadId;
    /** 服务端算出的总分片数（fileSize 除以 chunkSize 向上取整），超过 10000 会当场报错而不是建会话 */
    private int totalChunks;
    /** 这条会话锁定的切片大小。命中断点续传时它是库里已有的值，未必等于本次请求传的那个 */
    private int chunkSize;

    /**
     * true 表示 MD5 秒传命中：文件已在对象存储中，前端无需再传分片，
     * 直接调 complete 即可生成视频记录。
     */
    private boolean instant;

    /** 断点续传：服务端已确认收到的分片下标，前端跳过这些直接传剩余的 */
    private List<Integer> uploadedIndexes;
}
