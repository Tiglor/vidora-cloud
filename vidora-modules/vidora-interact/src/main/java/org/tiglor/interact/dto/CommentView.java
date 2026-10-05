package org.tiglor.interact.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 评论展示视图。
 * <p>
 * 只带 userId，不带昵称头像——用户资料在 user_service 库里，跨服务补全要一次批量查询。
 * 逐条调 Feign 会让一页 20 条评论变成 20 次远程调用，这件事应该由 BFF 聚合层批量做。
 * </p>
 */
@Data
public class CommentView {

    private Long id;
    private Long videoId;
    private Long userId;
    private Long parentId;
    private Long rootId;
    private String content;
    private Long likeCount;
    private Integer replyCount;

    /** 审核态：1-正常 2-审核中。用户端接口只会返回 1，这个字段是给后台评论管理列表用的 */
    private Integer status;

    private LocalDateTime createTime;

    /** 仅顶层评论填充：第一页回复，更多回复走 {@code GET /comments/replies} 翻页 */
    private List<CommentView> replies;
}
