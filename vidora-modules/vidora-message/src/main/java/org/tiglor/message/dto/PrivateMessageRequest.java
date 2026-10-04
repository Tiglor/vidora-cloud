package org.tiglor.message.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 发一条私信。发送者取当前登录用户，不接受客户端指定。 */
@Data
public class PrivateMessageRequest {

    @NotNull(message = "receiverId 不能为空")
    @Positive(message = "receiverId 非法")
    private Long receiverId;

    @NotBlank(message = "消息内容不能为空")
    @Size(max = 2000, message = "私信内容不能超过 2000 字")
    private String content;

    @Size(max = 2000, message = "extra 不能超过 2000 字")
    private String extra;
}
