package org.tiglor.video.dto;

import lombok.Data;

import java.util.List;

/**
 * 分片上传进度。下标清单实时来自对象存储，不用库里的 completed_chunks
 * ——后者在并发重传同一分片时可能偏大。
 */
@Data
public class MultipartProgress {

    private String uploadId;
    /** 0-上传中 1-分片已收齐 2-已合并 */
    private int status;
    private int totalChunks;
    private int chunkSize;
    private int uploadedCount;
    private List<Integer> uploadedIndexes;
}
