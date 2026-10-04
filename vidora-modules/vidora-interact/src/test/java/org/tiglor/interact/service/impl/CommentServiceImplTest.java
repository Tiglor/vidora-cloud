package org.tiglor.interact.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.tiglor.common.core.BizException;
import org.tiglor.common.test.MpTestSupport;
import org.tiglor.interact.dto.CommentCreateRequest;
import org.tiglor.interact.dto.CommentView;
import org.tiglor.interact.entity.Comment;
import org.tiglor.interact.mapper.CommentMapper;
import org.tiglor.interact.service.PlayCountService;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 评论的树形结构推导、删除权限与列表聚合。
 * <p>
 * 重点是 parent_id / root_id 的推导规则：不管回复套多少层，整棵树都必须能靠
 * 一条 {@code WHERE root_id = ?} 捞出来，否则楼中楼会退化成递归查询。
 * </p>
 * <p>
 * mock 的 Mapper 不执行条件也不做分页，所以 {@code selectPage} 的桩照真实 MyBatis-Plus
 * 的行为把传进来的 Page 填好再返回，这样分页参数的钳制才断言得到。
 * </p>
 */
class CommentServiceImplTest {

    private static final long AUTHOR = 42L;
    private static final long OTHER = 43L;
    private static final long VIDEO_ID = 7L;

    CommentMapper mapper;
    PlayCountService playCountService;
    CommentServiceImpl service;

    private final List<Wrapper<Comment>> pageWrappers = new ArrayList<>();
    private final List<LambdaUpdateWrapper<Comment>> updateWrappers = new ArrayList<>();
    private final List<Collection<Long>> previewCalls = new ArrayList<>();
    private List<Comment> pageRecords = List.of();
    private List<Comment> previewRows = List.of();

    @BeforeAll
    static void initTableInfo() {
        MpTestSupport.initTableInfo(Comment.class);
    }

    @BeforeEach
    void setUp() {
        mapper = mock(CommentMapper.class);
        playCountService = mock(PlayCountService.class);
        service = new CommentServiceImpl(playCountService);
        MpTestSupport.injectMapper(service, mapper, CommentMapper.class);

        when(mapper.insert(any(Comment.class))).thenReturn(1);
        when(mapper.selectPage(any(), any())).thenAnswer(invocation -> {
            pageWrappers.add(invocation.getArgument(1));
            Page<Comment> page = invocation.getArgument(0);
            page.setRecords(pageRecords);
            page.setTotal(pageRecords.size());
            return page;
        });
        when(mapper.update(any(), any())).thenAnswer(invocation -> {
            updateWrappers.add(invocation.getArgument(1));
            return 1;
        });
        when(mapper.previewReplies(any(), anyInt())).thenAnswer(invocation -> {
            previewCalls.add(invocation.getArgument(0));
            return previewRows;
        });
    }

    private String pageSql() {
        assertThat(pageWrappers).as("应该恰好发起一次分页查询").hasSize(1);
        return pageWrappers.get(0).getCustomSqlSegment();
    }

    private String lastUpdateSqlSet() {
        assertThat(updateWrappers).as("应该恰好发起一次条件更新").hasSize(1);
        return updateWrappers.get(0).getSqlSet();
    }

    private static Comment comment(long id, long parentId, long rootId) {
        Comment comment = new Comment();
        comment.setId(id);
        comment.setVideoId(VIDEO_ID);
        comment.setUserId(AUTHOR);
        comment.setParentId(parentId);
        comment.setRootId(rootId);
        comment.setContent("c" + id);
        comment.setLikeCount(0L);
        comment.setReplyCount(0);
        comment.setStatus(1);
        return comment;
    }

    private static CommentCreateRequest request(Long parentId, String content) {
        CommentCreateRequest request = new CommentCreateRequest();
        request.setVideoId(VIDEO_ID);
        request.setContent(content);
        request.setParentId(parentId);
        return request;
    }

    // ---------- publish ----------

    @Test
    @DisplayName("未登录不能发评论")
    void publishRequiresLogin() {
        assertThatThrownBy(() -> service.publish(request(null, "hi"), null))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("未登录");
    }

    @Test
    @DisplayName("顶层评论 parent_id / root_id 都是 0，且不触发回复数自增")
    void topLevelCommentStaysAtTheRoot() {
        CommentView view = service.publish(request(null, "  第一条  "), AUTHOR);

        assertThat(view.getContent()).isEqualTo("第一条");
        assertThat(view.getParentId()).isZero();
        assertThat(view.getRootId()).isZero();
        verify(mapper, never()).update(any(), any());
        verify(playCountService).accumulate(VIDEO_ID, 0, 0, 1, 0);
    }

    @Test
    @DisplayName("显式传 parentId=0 等同于顶层评论，不去查父评论")
    void explicitZeroParentIsTopLevel() {
        assertThat(service.publish(request(0L, "top"), AUTHOR).getRootId()).isZero();
        verify(mapper, never()).selectById(any());
    }

    @Test
    @DisplayName("回复顶层评论：root 就是父评论自己")
    void replyToTopLevelUsesTheParentAsRoot() {
        doReturn(comment(10L, 0L, 0L)).when(mapper).selectById(10L);

        CommentView view = service.publish(request(10L, "回复"), AUTHOR);

        assertThat(view.getParentId()).isEqualTo(10L);
        assertThat(view.getRootId()).isEqualTo(10L);
        assertThat(lastUpdateSqlSet()).contains("reply_count = reply_count + 1");
    }

    @Test
    @DisplayName("回复一条回复：沿用父级的 root，整棵树保持扁平")
    void replyToReplyFlattensToTheSameRoot() {
        doReturn(comment(11L, 10L, 10L)).when(mapper).selectById(11L);

        CommentView view = service.publish(request(11L, "楼中楼"), AUTHOR);

        assertThat(view.getParentId()).isEqualTo(11L);
        assertThat(view.getRootId()).isEqualTo(10L);
    }

    @Test
    @DisplayName("不能跨视频回复：否则评论树会挂到别的视频下")
    void crossVideoReplyIsRejected() {
        Comment parent = comment(10L, 0L, 0L);
        parent.setVideoId(8L);
        doReturn(parent).when(mapper).selectById(10L);

        assertThatThrownBy(() -> service.publish(request(10L, "回复"), AUTHOR))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("不能跨视频回复");
        verify(mapper, never()).insert(any(Comment.class));
    }

    @Test
    @DisplayName("回复一条正在审核的评论会被拒绝")
    void replyToAuditingCommentIsRejected() {
        Comment parent = comment(10L, 0L, 0L);
        parent.setStatus(2);
        doReturn(parent).when(mapper).selectById(10L);

        assertThatThrownBy(() -> service.publish(request(10L, "回复"), AUTHOR))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("审核中");
    }

    @Test
    @DisplayName("回复一条不存在的评论返回 404")
    void replyToMissingParentIsNotFound() {
        doReturn(null).when(mapper).selectById(999L);

        assertThatThrownBy(() -> service.publish(request(999L, "回复"), AUTHOR))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("被回复的评论不存在");
    }

    // ---------- list ----------

    @Test
    @DisplayName("视频评论列表只取顶层且审核通过的")
    void listByVideoOnlyShowsApprovedTopLevelComments() {
        service.listByVideo(VIDEO_ID, 1, 20, null);

        assertThat(pageSql())
                .contains("video_id =")
                .contains("status =")
                .contains("parent_id =");
    }

    @Test
    @DisplayName("默认按最新排序，用自增 id 代替 create_time")
    void defaultSortIsNewestFirst() {
        service.listByVideo(VIDEO_ID, 1, 20, null);

        assertThat(pageSql()).contains("ORDER BY id DESC").doesNotContain("like_count");
    }

    @Test
    @DisplayName("热门排序必须带 id 兜底：点赞数相同的大批评论翻页时会重复或被跳过")
    void hotSortFallsBackToIdAsATieBreaker() {
        service.listByVideo(VIDEO_ID, 1, 20, "HOT");

        assertThat(pageSql()).contains("ORDER BY like_count DESC,id DESC");
    }

    @Test
    @DisplayName("分页 size 被夹到 100，current 夹到至少 1")
    void thePageIsClamped() {
        Page<CommentView> page = service.listByVideo(VIDEO_ID, 0, 100_000, null);

        assertThat(page.getSize()).isEqualTo(100L);
        assertThat(page.getCurrent()).isEqualTo(1L);
    }

    @Test
    @DisplayName("整页顶层评论的预览回复一次查完，不做 N+1")
    void previewRepliesAreFetchedInOneQuery() {
        pageRecords = List.of(comment(1L, 0L, 0L), comment(2L, 0L, 0L));
        previewRows = List.of(comment(11L, 1L, 1L), comment(12L, 1L, 1L), comment(21L, 2L, 2L));

        Page<CommentView> page = service.listByVideo(VIDEO_ID, 1, 20, null);

        assertThat(previewCalls).containsExactly(List.of(1L, 2L));
        assertThat(page.getRecords()).hasSize(2);
        assertThat(page.getRecords().get(0).getReplies()).hasSize(2);
        assertThat(page.getRecords().get(1).getReplies()).hasSize(1);
    }

    @Test
    @DisplayName("空列表不去查预览回复")
    void emptyPageSkipsThePreviewQuery() {
        assertThat(service.listByVideo(VIDEO_ID, 1, 20, null).getRecords()).isEmpty();
        assertThat(previewCalls).isEmpty();
    }

    @Test
    @DisplayName("回复列表按时间正序，对话是从上往下读的")
    void repliesAreOrderedOldestFirst() {
        doReturn(comment(10L, 0L, 0L)).when(mapper).selectById(10L);
        pageRecords = List.of(comment(11L, 10L, 10L));

        Page<CommentView> page = service.listReplies(10L, 1, 20);

        assertThat(pageSql()).contains("root_id =").contains("ORDER BY id ASC");
        assertThat(page.getRecords()).hasSize(1);
        assertThat(previewCalls).as("回复列表不再嵌套预取").isEmpty();
    }

    @Test
    @DisplayName("根评论不存在时返回空页：绕过列表接口也翻不到回复")
    void repliesAreHiddenWhenTheRootIsMissing() {
        doReturn(null).when(mapper).selectById(10L);

        Page<CommentView> page = service.listReplies(10L, 1, 20);

        assertThat(page.getRecords()).isEmpty();
        assertThat(page.getTotal()).isZero();
        assertThat(pageWrappers).isEmpty();
    }

    @Test
    @DisplayName("根评论被屏蔽时整棵树都不露出来")
    void repliesAreHiddenWhenTheRootIsBlocked() {
        Comment hidden = comment(11L, 0L, 0L);
        hidden.setStatus(2);
        doReturn(hidden).when(mapper).selectById(11L);

        assertThat(service.listReplies(11L, 1, 20).getTotal()).isZero();
        assertThat(pageWrappers).isEmpty();
    }

    @Test
    @DisplayName("对一条回复调 listReplies 返回空页：只有顶层评论才有子树")
    void repliesOfAReplyYieldAnEmptyPage() {
        doReturn(comment(11L, 10L, 10L)).when(mapper).selectById(11L);

        assertThat(service.listReplies(11L, 1, 20).getRecords()).isEmpty();
        assertThat(pageWrappers).isEmpty();
    }

    // ---------- delete ----------

    @Test
    @DisplayName("别人的评论不能删")
    void deleteByStrangerIsForbidden() {
        doReturn(comment(10L, 0L, 0L)).when(mapper).selectById(10L);

        assertThatThrownBy(() -> service.delete(10L, OTHER, false))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("只能删除自己的评论");
        verify(mapper, never()).deleteById(any(Long.class));
    }

    @Test
    @DisplayName("作者本人可以删自己的评论")
    void deleteByAuthorIsAllowed() {
        doReturn(comment(10L, 0L, 0L)).when(mapper).selectById(10L);

        service.delete(10L, AUTHOR, false);

        verify(mapper).deleteById(10L);
    }

    @Test
    @DisplayName("审核者可以删任何人的评论")
    void deleteByModeratorIsAllowed() {
        doReturn(comment(10L, 0L, 0L)).when(mapper).selectById(10L);

        service.delete(10L, OTHER, true);

        verify(mapper).deleteById(10L);
    }

    @Test
    @DisplayName("删一条回复要同时回滚根评论的 reply_count 和视频的评论数")
    void deletingAReplyRollsBackBothCounters() {
        doReturn(comment(11L, 10L, 10L)).when(mapper).selectById(11L);

        service.delete(11L, AUTHOR, false);

        assertThat(lastUpdateSqlSet()).contains("GREATEST(reply_count - 1, 0)");
        verify(playCountService).accumulate(VIDEO_ID, 0, 0, -1, 0);
    }

    @Test
    @DisplayName("删顶层评论只回滚视频评论数，没有 reply_count 可减")
    void deletingATopLevelCommentOnlyRollsBackTheVideoCounter() {
        doReturn(comment(10L, 0L, 0L)).when(mapper).selectById(10L);

        service.delete(10L, AUTHOR, false);

        verify(mapper, never()).update(any(), any());
        verify(playCountService).accumulate(VIDEO_ID, 0, 0, -1, 0);
    }

    @Test
    @DisplayName("删一条不存在的评论返回 404")
    void deleteMissingCommentIsNotFound() {
        doReturn(null).when(mapper).selectById(999L);

        assertThatThrownBy(() -> service.delete(999L, AUTHOR, false))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("评论不存在");
    }

    @Test
    @DisplayName("删除要求登录")
    void deleteRequiresLogin() {
        assertThatThrownBy(() -> service.delete(10L, null, false))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("未登录");
    }

    // ---------- audit / counters ----------

    @Test
    @DisplayName("审核状态只接受 1-正常 / 2-审核中")
    void auditRejectsUnknownStatus() {
        assertThatThrownBy(() -> service.audit(10L, 9))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("status 只能是");
        verify(mapper, never()).update(any(), any());
    }

    @Test
    @DisplayName("审核一条不存在的评论返回 404")
    void auditMissingCommentIsNotFound() {
        doReturn(null).when(mapper).selectById(10L);

        assertThatThrownBy(() -> service.audit(10L, 2))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("评论不存在");
    }

    @Test
    @DisplayName("审核通过会落一条状态更新")
    void auditWritesTheStatus() {
        doReturn(comment(10L, 0L, 0L)).when(mapper).selectById(10L);

        service.audit(10L, 1);

        verify(mapper).update(any(), any());
    }

    @Test
    @DisplayName("点赞数自增走 setSql 原子加，不做读改写")
    void bumpLikeCountUsesAnAtomicIncrement() {
        service.bumpLikeCount(10L, true);

        assertThat(lastUpdateSqlSet()).contains("like_count = like_count + 1");
    }

    @Test
    @DisplayName("点赞数自减用 GREATEST 兜住，重复的取消请求不会压成负数")
    void bumpLikeCountNeverGoesNegative() {
        service.bumpLikeCount(10L, false);

        assertThat(lastUpdateSqlSet()).contains("GREATEST(like_count - 1, 0)");
    }

    @Test
    @DisplayName("commentId 为空时直接忽略，不发无意义的 UPDATE")
    void bumpLikeCountIgnoresNullId() {
        service.bumpLikeCount(null, true);

        verify(mapper, never()).update(any(), any());
    }

    @Test
    @DisplayName("countByVideo 在没有匹配行时返回 0 而不是 null")
    void countByVideoDefaultsToZero() {
        when(mapper.selectCount(any())).thenReturn(null);

        assertThat(service.countByVideo(VIDEO_ID)).isZero();
    }
}
