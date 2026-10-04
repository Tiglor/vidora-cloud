package org.tiglor.content.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 新建标签。只有名字一个字段——{@code use_count} 由 video-service 在打标签时维护，
 * {@code status} 走单独的启停接口，都不该在创建时由调用方指定。
 */
@Data
public class TagRequest {

    @NotBlank(message = "标签名不能为空")
    @Size(max = 50, message = "标签名不能超过 50 字")
    private String name;
}
