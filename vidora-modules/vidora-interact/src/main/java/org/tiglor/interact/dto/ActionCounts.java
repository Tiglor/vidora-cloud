package org.tiglor.interact.dto;

import lombok.Data;

/**
 * 某个互动对象的计数 + 当前登录用户的动作状态。
 * <p>
 * liked / favorited 在未登录时为 null（不是 false）——「没登录」和「登录了但没点过」
 * 对前端是两种展示：前者点下去要先跳登录，后者是取消。
 * </p>
 */
@Data
public class ActionCounts {

    private String targetType;
    private Long targetId;

    private Long likeCount;
    private Long favoriteCount;
    private Long shareCount;

    private Boolean liked;
    private Boolean favorited;
}
