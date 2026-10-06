package org.tiglor.interact.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 评论展示视图。
 * <p>
 * 只带 userId，不带昵称头像——sys_user 归 system-service 写，补全要一次批量查询。
 * 同库不等于可以直读：单库之后表边界靠归属守，跨域数据仍走服务间调用。
 * 逐条调 Feign 会让一页 20 条评论变成 20 次远程调用，这件事应该由 BFF 聚合层批量做。
 * </p>
 */
@Data
public class CommentView {

    /** 评论 ID。它同时是列表的排序游标，也是调「查看更多回复」时要传的 rootId */
    private Long id;

    /** 所属视频，原样来自评论行 */
    private Long videoId;

    /** 作者 ID，昵称头像不在这里给，见类注释 */
    private Long userId;

    /** 直接父评论；0 表示这是顶层评论，不会出现 null */
    private Long parentId;

    /** 所在楼中楼的根评论 ID；0 表示自己就是根。由服务端推导，客户端传的值不认 */
    private Long rootId;

    /** 正文，已去掉首尾空格 */
    private String content;

    /** 点赞数，仅在有人对这条评论点赞/取消时增减，与视频那条计数链路无关 */
    private Long likeCount;

    /** 回复总数。只有根评论上的值有意义，普通回复恒为 0；比 replies 的实际条数大说明还有更多页 */
    private Integer replyCount;

    /** 审核态：1-正常 2-审核中。用户端接口只会返回 1，这个字段是给后台评论管理列表用的 */
    private Integer status;

    /** 发表时刻。用户端列表的倒序分页实际按 id 排，因为同一秒可能有多条评论 */
    private LocalDateTime createTime;

    /** 仅顶层评论填充：第一页回复，更多回复走 {@code GET /comments/replies} 翻页 */
    private List<CommentView> replies;
}
