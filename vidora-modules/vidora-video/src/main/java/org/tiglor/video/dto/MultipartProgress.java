package org.tiglor.video.dto;

import lombok.Data;

import java.util.List;

/**
 * 分片上传进度。下标清单实时来自对象存储，不用库里的 completed_chunks
 * ——后者在并发重传同一分片时可能偏大。
 */
@Data
public class MultipartProgress {

    /** 查询时用的那个上传任务 ID，原样回显 */
    private String uploadId;
    /** 0-上传中 1-分片已收齐 2-已合并 */
    private int status;
    /** 会话登记的分片总数，切片参数定了就不会变 */
    private int totalChunks;
    /** 会话锁定的切片大小（字节），前端据此判断是否要重切 */
    private int chunkSize;
    /** 实测已收到的分片数，等于下面清单的长度，不是库里那个可能虚高的计数列 */
    private int uploadedCount;
    /**
     * 已收到的分片下标，升序、去重，直接列举对象存储得到。
     * <p>秒传命中的会话根本没有分片对象，此时按「全齐」返回 0 到 totalChunks-1 的完整清单。</p>
     */
    private List<Integer> uploadedIndexes;
}
