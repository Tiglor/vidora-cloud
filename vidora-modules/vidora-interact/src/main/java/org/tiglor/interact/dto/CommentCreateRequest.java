package org.tiglor.interact.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 发表评论 / 回复。
 * <p>
 * 刻意不接受 rootId：楼中楼的根必须由服务端从 parentId 推导。让客户端传的话，
 * 随便填一个 rootId 就能把回复挂到别人的评论树下，列表聚合直接错乱。
 * </p>
 */
@Data
public class CommentCreateRequest {

    @NotNull(message = "videoId 不能为空")
    private Long videoId;

    @NotBlank(message = "评论内容不能为空")
    @Size(max = 2000, message = "评论内容不能超过 2000 字")
    private String content;

    /** 被回复的评论 ID；顶层评论传 null 或 0 */
    private Long parentId;
}
