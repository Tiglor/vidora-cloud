package org.tiglor.interact.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

/** 点赞 / 收藏 / 分享请求。同一个接口表达「设置成某个状态」，因此天然幂等。 */
@Data
public class ActionRequest {

    /** 对象类型：video / comment */
    @NotBlank(message = "targetType 不能为空")
    private String targetType;

    /**
     * 被互动对象的 ID，与 {@link #targetType} 一起决定作用在哪条视频/哪条评论上。
     * <p>只校验是正数，不校验对象是否真的存在——跨服务校验会给这条高频写路径加一次远程调用。</p>
     */
    @NotNull(message = "targetId 不能为空")
    @Positive(message = "targetId 必须为正数")
    private Long targetId;

    /** 动作类型：1-点赞 2-收藏 3-分享 */
    @NotNull(message = "actionType 不能为空")
    private Integer actionType;

    /** false 表示取消。分享不支持取消 */
    private boolean active = true;
}
