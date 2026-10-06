package org.tiglor.video.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 合并分片并生成视频记录的请求。
 */
@Data
public class MultipartCompleteRequest {

    /**
     * {@code /init} 返回的上传任务 ID，只能操作属于自己的会话。
     * <p>合并幂等：同一个 uploadId 重复调用不会再生成第二条视频，而是把已登记的那条原样返回。</p>
     */
    @NotBlank(message = "uploadId 不能为空")
    private String uploadId;

    /** 留空时用原始文件名作标题 */
    private String title;
    /** 视频简介，原样写入，不做任何校验 */
    private String description;
    /** 分类 ID，原样写入；不传即留在未分类（列默认 0），也不校验这个分类是否存在 */
    private Long categoryId;
}
