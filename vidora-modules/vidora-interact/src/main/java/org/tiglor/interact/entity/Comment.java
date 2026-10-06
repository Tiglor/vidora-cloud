package org.tiglor.interact.entity;

import org.tiglor.common.core.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;


@Data
@EqualsAndHashCode(callSuper = true)
@TableName("interact_comment")
public class Comment extends BaseEntity {

    /** 所属视频。回复必须与父评论在同一条视频下，跨视频回复会被判为参数非法 */
    private Long videoId;

    /** 发表评论的用户，取当前登录用户；列表只回这个 ID，昵称头像由聚合层批量补 */
    private Long userId;

    /** 直接父评论。0 表示顶层评论，没有 null 这一档 */
    private Long parentId;

    /**
     * 整棵楼中楼的根评论 ID，0 表示自己就是根。
     * <p>由服务端从 parentId 推导——父级是顶层就取父级 id，父级本身是回复就沿用它的 root，
     * 客户端传什么不算数。于是回复套多少层都能用一条 {@code WHERE root_id = ?} 捞平。</p>
     */
    private Long rootId;

    /** 正文，入库前已 trim；非空与长度上限在请求 DTO 上校验，落库后不再修改 */
    private String content;

    /**
     * 点赞数冗余列，只对 target_type=comment 的点赞同步。
     * <p>用 {@code like_count = like_count ± 1} 的条件更新维护，避免并发读改写互相覆盖增量；
     * 减法有 GREATEST 兜底，重复请求不会把它压成负数。</p>
     */
    private Long likeCount;

    /**
     * 回复数冗余列，只有根评论（rootId 为 0）上的值有意义，普通回复行恒为 0。
     * <p>发回复时给所在树的根 +1、删回复时 -1，同样走条件更新而非读改写。</p>
     */
    private Integer replyCount;

    /**
     * 审核状态：1-正常 2-审核中。
     * <p>表上还留着 0-删除这一档，但代码里没有任何写入路径——删除走 is_deleted 逻辑删除，
     * 所以实际只会看到 1 和 2。审核中的评论不进用户端列表，也不能被回复。</p>
     */
    private Integer status;
}
