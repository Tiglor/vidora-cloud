package org.tiglor.video.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 合并分片并生成视频记录的请求。
 */
@Data
public class MultipartCompleteRequest {

    @NotBlank(message = "uploadId 不能为空")
    private String uploadId;

    /** 留空时用原始文件名作标题 */
    private String title;
    private String description;
    private Long categoryId;
}
