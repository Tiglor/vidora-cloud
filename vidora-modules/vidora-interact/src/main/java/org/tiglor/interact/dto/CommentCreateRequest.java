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

    /**
     * 要评论的视频。
     * <p>回复时服务端会核对被回复的评论确实属于这条视频，跨视频回复直接判参数非法。</p>
     */
    @NotNull(message = "videoId 不能为空")
    private Long videoId;

    /** 正文，落库前会被 trim——只填空格过不了非空校验；不校验视频是否真实存在 */
    @NotBlank(message = "评论内容不能为空")
    @Size(max = 2000, message = "评论内容不能超过 2000 字")
    private String content;

    /** 被回复的评论 ID；顶层评论传 null 或 0 */
    private Long parentId;
}
