package org.tiglor.interact.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;
import org.tiglor.common.core.BizException;
import org.tiglor.common.test.MpTestSupport;
import org.tiglor.interact.dto.ActionCounts;
import org.tiglor.interact.dto.ActionTypeCount;
import org.tiglor.interact.entity.InteractAction;
import org.tiglor.interact.enums.ActionType;
import org.tiglor.interact.enums.TargetType;
import org.tiglor.interact.mapper.InteractActionMapper;
import org.tiglor.interact.service.CommentService;
import org.tiglor.interact.service.PlayCountService;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 点赞 / 收藏 / 分享的翻转语义与冗余计数的联动。
 * <p>
 * 这里的核心不变量是「计数只在状态真的翻转时移动」：mock 的 Mapper 不会执行
 * {@code status <> ?} 这个条件，所以由测试用 {@link #updateResult} 模拟数据库的
 * 「影响 0 行 / 1 行」，验证服务层对这个信号的反应。
 * </p>
 */
class InteractActionServiceImplTest {

    private static final long USER_ID = 42L;
    private static final long VIDEO_ID = 7L;
    private static final long COMMENT_ID = 99L;

    InteractActionMapper mapper;
    CommentService commentService;
    PlayCountService playCountService;
    InteractActionServiceImpl service;

    /** 模拟条件 UPDATE 的影响行数：0 表示状态已经是期望值（或行还不存在） */
    private int updateResult = 1;
    /** 模拟「这个用户对这个对象的这类动作已有几行」 */
    private long existingRows = 0L;

    private final List<Wrapper<InteractAction>> updateWrappers = new ArrayList<>();

    @BeforeAll
    static void initTableInfo() {
        MpTestSupport.initTableInfo(InteractAction.class);
    }

    @BeforeEach
    void setUp() {
        mapper = mock(InteractActionMapper.class);
        commentService = mock(CommentService.class);
        playCountService = mock(PlayCountService.class);
        service = new InteractActionServiceImpl(commentService, playCountService);
        MpTestSupport.injectMapper(service, mapper, InteractActionMapper.class);

        when(mapper.update(any(), any())).thenAnswer(invocation -> {
            updateWrappers.add(invocation.getArgument(1));
            return updateResult;
        });
        when(mapper.selectCount(any())).thenAnswer(invocation -> existingRows);
        when(mapper.insert(any(InteractAction.class))).thenReturn(1);
        when(mapper.countByTarget(anyString(), anyLong())).thenReturn(List.of());
        when(mapper.selectList(any())).thenReturn(List.of());
        when(mapper.selectPage(any(), any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private static ActionTypeCount count(ActionType type, long total) {
        ActionTypeCount row = new ActionTypeCount();
        row.setActionType(type.getCode());
        row.setTotal(total);
        return row;
    }

    private static InteractAction mine(ActionType type) {
        InteractAction action = new InteractAction();
        action.setUserId(USER_ID);
        action.setActionType(type.getCode());
        action.setStatus(1);
        return action;
    }

    @Test
    @DisplayName("首次点赞：插入一行并把 like_count 加一")
    void firstLikeInsertsAndBumpsCounters() {
        updateResult = 0;

        service.setActive(TargetType.VIDEO, VIDEO_ID, ActionType.LIKE, true, USER_ID);

        ArgumentCaptor<InteractAction> captor = ArgumentCaptor.forClass(InteractAction.class);
        verify(mapper).insert(captor.capture());
        InteractAction inserted = captor.getValue();
        assertThat(inserted.getUserId()).isEqualTo(USER_ID);
        assertThat(inserted.getTargetType()).isEqualTo("video");
        assertThat(inserted.getTargetId()).isEqualTo(VIDEO_ID);
        assertThat(inserted.getActionType()).isEqualTo(ActionType.LIKE.getCode());
        assertThat(inserted.getStatus()).isEqualTo(1);
        verify(playCountService).accumulate(VIDEO_ID, 0, 1, 0, 0);
    }

    @Test
    @DisplayName("重复点赞不重复加计数：状态没翻转，计数就不动")
    void repeatLikeDoesNotBumpCounters() {
        updateResult = 0;
        existingRows = 1L;

        service.setActive(TargetType.VIDEO, VIDEO_ID, ActionType.LIKE, true, USER_ID);

        verify(mapper, never()).insert(any(InteractAction.class));
        verifyNoInteractions(playCountService);
        verifyNoInteractions(commentService);
    }

    @Test
    @DisplayName("取消点赞：翻转状态并把 like_count 减一，不再插入")
    void unlikeFlipsStatusAndDecrements() {
        service.setActive(TargetType.VIDEO, VIDEO_ID, ActionType.LIKE, false, USER_ID);

        verify(mapper, never()).insert(any(InteractAction.class));
        verify(playCountService).accumulate(VIDEO_ID, 0, -1, 0, 0);
    }

    @Test
    @DisplayName("取消的动作写成条件 UPDATE，而不是读出来改再写回")
    void theFlipIsExpressedAsAConditionalUpdate() {
        service.setActive(TargetType.VIDEO, VIDEO_ID, ActionType.LIKE, false, USER_ID);

        assertThat(updateWrappers).hasSize(1);
        assertThat(updateWrappers.get(0).getCustomSqlSegment())
                .contains("user_id =")
                .contains("target_type =")
                .contains("target_id =")
                .contains("action_type =")
                // 把「状态确实需要变」写进 WHERE，两个并发请求不会各自读改写而丢掉一次变更
                .contains("status <>");
    }

    @Test
    @DisplayName("分享累计到 share_count")
    void shareBumpsTheShareCounter() {
        service.setActive(TargetType.VIDEO, VIDEO_ID, ActionType.SHARE, true, USER_ID);

        verify(playCountService).accumulate(VIDEO_ID, 0, 0, 0, 1);
    }

    @Test
    @DisplayName("分享是「发生过的事」，不支持取消")
    void shareCannotBeCancelled() {
        assertThatThrownBy(() -> service.setActive(TargetType.VIDEO, VIDEO_ID, ActionType.SHARE, false, USER_ID))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("分享不支持取消");

        verifyNoInteractions(playCountService);
    }

    @Test
    @DisplayName("给评论点赞走评论自己的 like_count，不动视频的计数表")
    void likeOnCommentBumpsTheCommentCounter() {
        service.setActive(TargetType.COMMENT, COMMENT_ID, ActionType.LIKE, true, USER_ID);

        verify(commentService).bumpLikeCount(COMMENT_ID, true);
        verifyNoInteractions(playCountService);
    }

    @Test
    @DisplayName("收藏不进任何计数列：它只影响「我的收藏」列表")
    void favoriteDoesNotTouchAnyCounter() {
        updateResult = 0;

        service.setActive(TargetType.VIDEO, VIDEO_ID, ActionType.FAVORITE, true, USER_ID);

        verify(mapper).insert(any(InteractAction.class));
        verifyNoInteractions(playCountService);
        verifyNoInteractions(commentService);
    }

    @Test
    @DisplayName("并发插入被唯一键挡下时按成功处理，但计数不能加第二次")
    void concurrentInsertIsSwallowedWithoutDoubleCounting() {
        updateResult = 0;
        when(mapper.insert(any(InteractAction.class)))
                .thenThrow(new DuplicateKeyException("uk_user_target_action"));

        ActionCounts counts = service.setActive(TargetType.VIDEO, VIDEO_ID, ActionType.LIKE, true, USER_ID);

        assertThat(counts).isNotNull();
        verifyNoInteractions(playCountService);
    }

    @Test
    @DisplayName("未登录不能做互动动作")
    void setActiveRequiresLogin() {
        assertThatThrownBy(() -> service.setActive(TargetType.VIDEO, VIDEO_ID, ActionType.LIKE, true, null))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("未登录");
    }

    @Test
    @DisplayName("非法 targetId 被拒绝")
    void setActiveRejectsInvalidTargetId() {
        assertThatThrownBy(() -> service.setActive(TargetType.VIDEO, 0L, ActionType.LIKE, true, USER_ID))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("targetId");
    }

    @Test
    @DisplayName("分组计数映射到三个字段，缺的类型补 0")
    void countsMapsGroupedRowsAndFillsMissingTypesWithZero() {
        when(mapper.countByTarget("video", VIDEO_ID))
                .thenReturn(List.of(count(ActionType.LIKE, 5L), count(ActionType.SHARE, 3L)));
        when(mapper.selectList(any())).thenReturn(List.of(mine(ActionType.LIKE)));

        ActionCounts counts = service.counts(TargetType.VIDEO, VIDEO_ID, USER_ID);

        assertThat(counts.getTargetType()).isEqualTo("video");
        assertThat(counts.getTargetId()).isEqualTo(VIDEO_ID);
        assertThat(counts.getLikeCount()).isEqualTo(5L);
        assertThat(counts.getShareCount()).isEqualTo(3L);
        assertThat(counts.getFavoriteCount()).isZero();
        assertThat(counts.getLiked()).isTrue();
        assertThat(counts.getFavorited()).isFalse();
    }

    @Test
    @DisplayName("未登录时 liked / favorited 保持 null：前端要区分「没登录」和「登录了但没点过」")
    void anonymousCountsLeaveTheBooleansNull() {
        when(mapper.countByTarget("video", VIDEO_ID)).thenReturn(List.of(count(ActionType.LIKE, 5L)));

        ActionCounts counts = service.counts(TargetType.VIDEO, VIDEO_ID, null);

        assertThat(counts.getLikeCount()).isEqualTo(5L);
        assertThat(counts.getLiked()).isNull();
        assertThat(counts.getFavorited()).isNull();
        verify(mapper, never()).selectList(any());
    }

    @Test
    @DisplayName("我的收藏分页把 size 夹到 100，current 夹到至少 1")
    void myFavoritesClampsThePage() {
        assertThat(service.myFavorites(USER_ID, 0, 100_000).getSize()).isEqualTo(100L);
        assertThat(service.myFavorites(USER_ID, 0, 100_000).getCurrent()).isEqualTo(1L);
    }

    @Test
    @DisplayName("我的收藏要求登录")
    void myFavoritesRequiresLogin() {
        assertThatThrownBy(() -> service.myFavorites(null, 1, 20))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("未登录");
    }
}
