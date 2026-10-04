package org.tiglor.interact.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.tiglor.interact.dto.CommentCreateRequest;
import org.tiglor.interact.dto.CommentView;

/**
 * 评论与回复（两级楼中楼）。
 * <p>
 * 展示模型是「顶层评论分页 + 每条挂第一页回复」，所以同时存了 parent_id（直接父级，
 * 用来渲染「回复 @某人」）和 root_id（顶层祖先，用来聚合整棵树）。
 * 只存 parent_id 的话，取某条顶层评论的全部回复要递归查 N 层；有了 root_id 就是一条
 * {@code WHERE root_id = ?} 的索引查询。
 * </p>
 * <p>
 * {@code status} 只表达审核态（1-正常 2-审核中），删除走 is_deleted 逻辑删除——
 * 两套机制各管一件事，混用会让「被删」和「待审」在查询条件里纠缠不清。
 * </p>
 */
public interface CommentService {

    /** 发表评论或回复，root_id 由服务端从 parent_id 推导，同时维护根评论的 reply_count */
    CommentView publish(CommentCreateRequest request, Long userId);

    /**
     * 某视频的顶层评论分页。
     *
     * @param sort {@code hot}（按点赞数）或 {@code new}（按时间，默认）
     */
    Page<CommentView> listByVideo(Long videoId, long current, long size, String sort);

    /** 某条顶层评论下的全部回复分页（楼中楼「查看更多回复」） */
    Page<CommentView> listReplies(Long rootId, long current, long size);

    /**
     * 删除评论（逻辑删除）。作者本人可删自己的，审核者可删任何一条。
     * 删的是回复时会把根评论的 reply_count 减回去。
     */
    void delete(Long commentId, Long userId, boolean moderator);

    /** 审核：置为正常或审核中 */
    void audit(Long commentId, int status);

    /** 某视频的有效评论总数 */
    long countByVideo(Long videoId);

    /**
     * 维护评论上的点赞数冗余列（真正的记录在 {@code interact_action}）。
     * <p>
     * 必须有这个冗余列：热度排序要 {@code ORDER BY like_count}，
     * 跨表 COUNT 出来的值没法直接参与分页排序。
     * </p>
     *
     * @param increment true 表示新增一个点赞，false 表示取消
     */
    void bumpLikeCount(Long commentId, boolean increment);
}
