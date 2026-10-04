package org.tiglor.video.dto;

import lombok.Data;

import java.util.List;

/**
 * 分片上传初始化结果。
 */
@Data
public class MultipartInitResult {

    private String uploadId;
    private int totalChunks;
    private int chunkSize;

    /**
     * true 表示 MD5 秒传命中：文件已在对象存储中，前端无需再传分片，
     * 直接调 complete 即可生成视频记录。
     */
    private boolean instant;

    /** 断点续传：服务端已确认收到的分片下标，前端跳过这些直接传剩余的 */
    private List<Integer> uploadedIndexes;
}
