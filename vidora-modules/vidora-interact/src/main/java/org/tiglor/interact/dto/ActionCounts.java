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

    /** 这次算的是哪类对象：video / comment，与 {@link #targetId} 一起定位互动对象 */
    private String targetType;

    /** 被查询对象的 ID，原样回显请求参数，便于前端把响应贴回对应的卡片上 */
    private Long targetId;

    /**
     * 有效点赞数，即该对象下 status=1 的点赞行数。
     * <p>每次请求现查 {@code interact_action}，因此没有缓存延迟；{@code VideoTotals} 上的同名计数
     * 走的是按天 SUM + 60 秒缓存那条路径，两者可以差出一分钟内的增减。</p>
     */
    private Long likeCount;

    /** 有效收藏数。收藏只体现在这里和「我的收藏」列表，不写进任何按天计数列 */
    private Long favoriteCount;

    /** 分享次数。分享不支持取消，所以这一项一旦产生就一直算作有效 */
    private Long shareCount;

    /** 当前用户是否点赞过该对象；未登录时为 null，见类注释 */
    private Boolean liked;

    /** 当前用户是否收藏了该对象；取消过会回到 false，未登录时为 null */
    private Boolean favorited;
}
