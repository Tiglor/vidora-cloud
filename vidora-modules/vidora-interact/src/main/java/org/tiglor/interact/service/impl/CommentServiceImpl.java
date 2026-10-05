package org.tiglor.interact.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.tiglor.common.core.BizException;
import org.tiglor.common.core.ResultCode;
import org.tiglor.interact.dto.CommentCreateRequest;
import org.tiglor.interact.dto.CommentView;
import org.tiglor.interact.entity.Comment;
import org.tiglor.interact.mapper.CommentMapper;
import org.tiglor.interact.service.CommentService;
import org.tiglor.interact.service.PlayCountService;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CommentServiceImpl extends ServiceImpl<CommentMapper, Comment> implements CommentService {

    private static final int STATUS_NORMAL = 1;
    private static final int STATUS_AUDITING = 2;

    /** parent_id / root_id 为 0 表示顶层评论 */
    private static final long TOP_LEVEL = 0L;

    private static final long MAX_PAGE_SIZE = 100L;

    /** 顶层评论列表里每条预取的回复数，够铺首屏；「查看更多回复」走 listReplies */
    private static final int PREVIEW_REPLIES = 3;

    private static final String SORT_HOT = "hot";

    private final PlayCountService playCountService;

    @Override
    public CommentView publish(CommentCreateRequest request, Long userId) {
        requireUserId(userId);
        long parentId = request.getParentId() == null ? TOP_LEVEL : request.getParentId();
        long rootId = TOP_LEVEL;

        if (parentId != TOP_LEVEL) {
            Comment parent = getById(parentId);
            if (parent == null) {
                throw new BizException(ResultCode.NOT_FOUND, "被回复的评论不存在：" + parentId);
            }
            if (!Objects.equals(parent.getVideoId(), request.getVideoId())) {
                throw new BizException(ResultCode.VALIDATE_FAILED, "不能跨视频回复评论");
            }
            if (parent.getStatus() != null && parent.getStatus() == STATUS_AUDITING) {
                throw new BizException(ResultCode.VALIDATE_FAILED, "该评论正在审核中，暂不能回复");
            }
            // 父级是顶层 → 它就是 root；父级本身是回复 → 沿用它的 root。
            // 于是无论回复套多少层，整棵树都是一条 WHERE root_id = ? 就能捞出来的扁平结构
            rootId = isTopLevel(parent.getRootId()) ? parent.getId() : parent.getRootId();
        }

        Comment comment = new Comment();
        comment.setVideoId(request.getVideoId());
        comment.setUserId(userId);
        comment.setParentId(parentId);
        comment.setRootId(rootId);
        comment.setContent(request.getContent().trim());
        comment.setLikeCount(0L);
        comment.setReplyCount(0);
        comment.setStatus(STATUS_NORMAL);
        save(comment);

        if (rootId != TOP_LEVEL) {
            bumpReplyCount(rootId, true);
        }
        playCountService.accumulate(request.getVideoId(), 0, 0, 1, 0);
        return toView(comment);
    }

    @Override
    public Page<CommentView> listByVideo(Long videoId, long current, long size, String sort) {
        var query = lambdaQuery()
                .eq(Comment::getVideoId, videoId)
                .eq(Comment::getStatus, STATUS_NORMAL)
                .eq(Comment::getParentId, TOP_LEVEL);
        if (SORT_HOT.equalsIgnoreCase(sort)) {
            // 必须带一个唯一的兜底排序键：点赞数相同的大批评论在翻页时会重复出现或被跳过
            query.orderByDesc(Comment::getLikeCount).orderByDesc(Comment::getId);
        } else {
            // 用自增 id 代替 create_time：同一秒内可能有多条评论，id 天然唯一且是主键，排序更便宜
            query.orderByDesc(Comment::getId);
        }
        Page<Comment> page = query.page(newPage(current, size));
        return toViewPage(page, true);
    }

    @Override
    public Page<CommentView> listReplies(Long rootId, long current, long size) {
        // 根评论被删或被屏蔽时整棵树都不该再露出来，否则 listByVideo 已经不显示它、
        // 直接调这个接口却还能翻到全部回复
        Comment root = getById(rootId);
        if (root == null || !isTopLevel(root.getParentId())
                || (root.getStatus() != null && root.getStatus() != STATUS_NORMAL)) {
            Page<CommentView> empty = new Page<>(Math.max(current, 1), clampSize(size), 0);
            empty.setRecords(List.of());
            return empty;
        }
        Page<Comment> page = lambdaQuery()
                .eq(Comment::getRootId, rootId)
                .eq(Comment::getStatus, STATUS_NORMAL)
                // 回复按时间正序，对话是从上往下读的
                .orderByAsc(Comment::getId)
                .page(newPage(current, size));
        return toViewPage(page, false);
    }

    @Override
    public Page<CommentView> pageForAdmin(String keyword, Integer status, long current, long size) {
        String fuzzy = keyword == null || keyword.isBlank() ? null : keyword.trim();
        Page<Comment> page = lambdaQuery()
                .like(fuzzy != null, Comment::getContent, fuzzy)
                .eq(status != null, Comment::getStatus, status)
                .orderByDesc(Comment::getId)
                .page(newPage(current, size));
        // 不预取回复：后台列表里一行本身可能就是回复，给它挂 replies 没有意义
        return toViewPage(page, false);
    }

    @Override
    public void delete(Long commentId, Long userId, boolean moderator) {
        requireUserId(userId);
        Comment comment = getById(commentId);
        if (comment == null) {
            throw new BizException(ResultCode.NOT_FOUND, "评论不存在：" + commentId);
        }
        // 作者本人或审核者可删。审核者身份由控制器的 @PreAuthorize 判定后传进来，
        // 不在这一层再读一次权限上下文，方便复用与测试
        if (!moderator && !userId.equals(comment.getUserId())) {
            throw new BizException(ResultCode.FORBIDDEN, "只能删除自己的评论");
        }
        removeById(commentId);

        long rootId = comment.getRootId() == null ? TOP_LEVEL : comment.getRootId();
        if (rootId != TOP_LEVEL) {
            bumpReplyCount(rootId, false);
        }
        playCountService.accumulate(comment.getVideoId(), 0, 0, -1, 0);
    }

    @Override
    public void audit(Long commentId, int status) {
        if (status != STATUS_NORMAL && status != STATUS_AUDITING) {
            throw new BizException(ResultCode.VALIDATE_FAILED, "status 只能是 1-正常 或 2-审核中");
        }
        if (getById(commentId) == null) {
            throw new BizException(ResultCode.NOT_FOUND, "评论不存在：" + commentId);
        }
        lambdaUpdate()
                .set(Comment::getStatus, status)
                .eq(Comment::getId, commentId)
                .update();
    }

    @Override
    public long countByVideo(Long videoId) {
        Long count = lambdaQuery()
                .eq(Comment::getVideoId, videoId)
                .eq(Comment::getStatus, STATUS_NORMAL)
                .count();
        return count == null ? 0L : count;
    }

    /**
     * 把根评论的 reply_count 加一 / 减一。
     * <p>
     * 走 {@code set reply_count = reply_count ± 1} 的条件更新而不是读改写：
     * 同一条热门评论下的并发回复会互相覆盖增量。减法用 GREATEST 兜住，
     * 重复的删除请求不会把它压成负数。
     * </p>
     */
    private void bumpReplyCount(long rootId, boolean increment) {
        lambdaUpdate()
                .setSql(increment
                        ? "reply_count = reply_count + 1"
                        : "reply_count = GREATEST(reply_count - 1, 0)")
                .eq(Comment::getId, rootId)
                .update();
    }

    @Override
    public void bumpLikeCount(Long commentId, boolean increment) {
        if (commentId == null) {
            return;
        }
        // 和 reply_count 一样用条件更新，并发点赞不会互相覆盖增量；减法用 GREATEST 兜住不为负
        lambdaUpdate()
                .setSql(increment
                        ? "like_count = like_count + 1"
                        : "like_count = GREATEST(like_count - 1, 0)")
                .eq(Comment::getId, commentId)
                .update();
    }

    private Page<CommentView> toViewPage(Page<Comment> page, boolean withPreviewReplies) {
        Page<CommentView> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        List<CommentView> views = page.getRecords().stream()
                .map(this::toView)
                .collect(Collectors.toList());
        if (withPreviewReplies) {
            attachPreviewReplies(views);
        }
        result.setRecords(views);
        return result;
    }

    /** 一次查询补齐整页顶层评论的预览回复，避免 N+1 */
    private void attachPreviewReplies(List<CommentView> topLevelViews) {
        if (topLevelViews.isEmpty()) {
            return;
        }
        List<Long> rootIds = topLevelViews.stream().map(CommentView::getId).toList();
        Map<Long, List<CommentView>> byRoot = baseMapper.previewReplies(rootIds, PREVIEW_REPLIES).stream()
                .map(this::toView)
                .collect(Collectors.groupingBy(CommentView::getRootId, LinkedHashMap::new, Collectors.toList()));
        topLevelViews.forEach(view -> view.setReplies(byRoot.getOrDefault(view.getId(), List.of())));
    }

    private CommentView toView(Comment comment) {
        CommentView view = new CommentView();
        view.setId(comment.getId());
        view.setVideoId(comment.getVideoId());
        view.setUserId(comment.getUserId());
        view.setParentId(comment.getParentId());
        view.setRootId(comment.getRootId());
        view.setContent(comment.getContent());
        view.setLikeCount(comment.getLikeCount());
        view.setReplyCount(comment.getReplyCount());
        view.setStatus(comment.getStatus());
        view.setCreateTime(comment.getCreateTime());
        return view;
    }

    private static boolean isTopLevel(Long id) {
        return id == null || id == TOP_LEVEL;
    }

    private static Page<Comment> newPage(long current, long size) {
        return new Page<>(Math.max(current, 1), clampSize(size));
    }

    private static long clampSize(long size) {
        return Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
    }

    private static void requireUserId(Long userId) {
        if (userId == null) {
            throw new BizException(ResultCode.UNAUTHORIZED, "未登录");
        }
    }
}
