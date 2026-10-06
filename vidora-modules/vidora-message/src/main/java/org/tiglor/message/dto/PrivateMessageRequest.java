package org.tiglor.message.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 发一条私信。发送者取当前登录用户，不接受客户端指定。 */
@Data
public class PrivateMessageRequest {

    /** 私信发给谁；不能填自己（服务端会拒），发信人取当前登录用户而非这里的值 */
    @NotNull(message = "receiverId 不能为空")
    @Positive(message = "receiverId 非法")
    private Long receiverId;

    /** 私信正文，入库前会去掉首尾空白，因此只填空格过不了非空校验 */
    @NotBlank(message = "消息内容不能为空")
    @Size(max = 2000, message = "私信内容不能超过 2000 字")
    private String content;

    /** 可空的扩展字段，原样存进 JSON 列，服务端不解析其结构；收件人一侧才看得到这条私信 */
    @Size(max = 2000, message = "extra 不能超过 2000 字")
    private String extra;
}
