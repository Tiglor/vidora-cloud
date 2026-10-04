package org.tiglor.message.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 发一条系统通知或互动消息。
 * <p>
 * 只允许持有 {@code message:send} 的调用方使用（管理后台，或将来的 MQ 消费者）。
 * 普通用户能发的是 {@link PrivateMessageRequest}——如果这个接口对所有人开放，
 * 谁都能给别人伪造一条「你的视频已被下架」。
 * </p>
 */
@Data
public class NotifyRequest {

    /** 只能是 1-系统通知 或 2-互动消息；私信走 {@link PrivateMessageRequest} */
    @NotNull(message = "msgType 不能为空")
    private Integer msgType;

    @NotNull(message = "receiverId 不能为空")
    @Positive(message = "receiverId 非法")
    private Long receiverId;

    @NotBlank(message = "消息内容不能为空")
    @Size(max = 2000, message = "消息内容不能超过 2000 字")
    private String content;

    /** 扩展字段，原样存进 JSON 列（跳转链接、被点赞的对象 id 之类） */
    @Size(max = 2000, message = "extra 不能超过 2000 字")
    private String extra;
}
