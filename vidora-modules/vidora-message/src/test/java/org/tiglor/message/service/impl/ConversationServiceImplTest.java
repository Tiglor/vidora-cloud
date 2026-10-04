package org.tiglor.message.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.tiglor.common.core.BizException;
import org.tiglor.common.test.MpTestSupport;
import org.tiglor.message.dto.ConversationView;
import org.tiglor.message.entity.MessageConversation;
import org.tiglor.message.entity.MessageRecord;
import org.tiglor.message.mapper.MessageConversationMapper;
import org.tiglor.message.mapper.MessageRecordMapper;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 会话表的核心性质：双方归一化成 (min, max)，以及「我的未读数」要挑对列。
 * <p>
 * 归一化一旦漏掉，同一段对话会劈成两行，两边各看各的未读，谁也不知道对面已经回过话；
 * 挑错列则会把对方的未读数显示给我。这两件事都没法靠肉眼看 SQL 发现，只能钉在测试里。
 * </p>
 */
class ConversationServiceImplTest {

    private static final long ME = 42L;
    private static final long PEER = 7L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 3, 12, 0);

    MessageConversationMapper mapper;
    MessageRecordMapper recordMapper;
    ConversationServiceImpl service;

    private final List<Wrapper<MessageConversation>> pageWrappers = new ArrayList<>();
    private final List<Wrapper<MessageRecord>> previewWrappers = new ArrayList<>();
    private List<MessageConversation> pageRecords = List.of();

    @BeforeAll
    static void initTableInfo() {
        MpTestSupport.initTableInfo(MessageConversation.class, MessageRecord.class);
    }

    @BeforeEach
    void setUp() {
        mapper = mock(MessageConversationMapper.class);
        recordMapper = mock(MessageRecordMapper.class);
        service = new ConversationServiceImpl(recordMapper);
        MpTestSupport.injectMapper(service, mapper, MessageConversationMapper.class);

        when(mapper.selectPage(any(), any())).thenAnswer(invocation -> {
            pageWrappers.add(invocation.getArgument(1));
            Page<MessageConversation> page = invocation.getArgument(0);
            page.setRecords(pageRecords);
            page.setTotal(pageRecords.size());
            return page;
        });
        when(recordMapper.selectList(any())).thenAnswer(invocation -> {
            previewWrappers.add(invocation.getArgument(0));
            return List.of();
        });
    }

    private String pageSql() {
        assertThat(pageWrappers).as("应该恰好发起一次分页查询").hasSize(1);
        return pageWrappers.get(0).getCustomSqlSegment();
    }

    private static MessageConversation conversation(long id, long userIdA, long userIdB,
                                                    Long lastMsgId, int unreadA, int unreadB) {
        MessageConversation row = new MessageConversation();
        row.setId(id);
        row.setUserIdA(userIdA);
        row.setUserIdB(userIdB);
        row.setLastMsgId(lastMsgId);
        row.setLastMsgTime(NOW);
        row.setUnreadCountA(unreadA);
        row.setUnreadCountB(unreadB);
        return row;
    }

    private static MessageRecord message(long id, String content) {
        MessageRecord record = new MessageRecord();
        record.setId(id);
        record.setContent(content);
        record.setMsgType(3);
        record.setStatus(1);
        return record;
    }

    // ---------- touchOnSend ----------

    @Test
    @DisplayName("发件人 id 较大时被换到 B 侧，未读增量跟着收信人落在 A 侧")
    void touchOnSendSwapsThePairWhenTheSenderIsTheLargerId() {
        service.touchOnSend(ME, PEER, 555L, NOW);

        // pair = (7, 42)，收信人 7 是 A
        verify(mapper).touchOnSend(PEER, ME, 555L, NOW, 1, 0);
    }

    @Test
    @DisplayName("发件人 id 较小时保持原序，未读增量落在 B 侧")
    void touchOnSendKeepsTheOrderWhenTheSenderIsTheSmallerId() {
        service.touchOnSend(PEER, ME, 555L, NOW);

        verify(mapper).touchOnSend(PEER, ME, 555L, NOW, 0, 1);
    }

    @Test
    @DisplayName("同一对用户不管谁发，落到的都是唯一键上的同一行")
    void touchOnSendIsSymmetricInTheStoredKey() {
        service.touchOnSend(3L, PEER, 556L, NOW);
        service.touchOnSend(PEER, 3L, 557L, NOW);

        // 收信人 7 是 B → (0, 1)；收信人 3 是 A → (1, 0)
        verify(mapper).touchOnSend(3L, 7L, 556L, NOW, 0, 1);
        verify(mapper).touchOnSend(3L, 7L, 557L, NOW, 1, 0);
    }

    @Test
    @DisplayName("缺任何一方的会话都建不起来，否则唯一键上会多出一行 user_id 为 NULL 的垃圾")
    void touchOnSendRejectsAMissingParticipant() {
        assertThatThrownBy(() -> service.touchOnSend(null, ME, 1L, NOW))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("会话双方都不能为空");
        assertThatThrownBy(() -> service.touchOnSend(ME, null, 1L, NOW))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("会话双方都不能为空");
        verify(mapper, never()).touchOnSend(any(), any(), any(), any(), anyInt(), anyInt());
    }

    // ---------- list ----------

    @Test
    @DisplayName("会话列表覆盖「我是 A」和「我是 B」两种存法，并按最后消息时间倒序")
    void listScopesToMeOnEitherSideAndOrdersByRecency() {
        service.list(ME, 1, 20);

        assertThat(pageSql())
                .contains("user_id_a =")
                .contains("user_id_b =")
                .contains("OR")
                .contains("ORDER BY last_msg_time DESC,id DESC");
    }

    @Test
    @DisplayName("对面是谁、我有几条未读，都按我在这一行里是 A 还是 B 挑对列")
    void listResolvesThePeerAndMyOwnUnreadSide() {
        pageRecords = List.of(
                conversation(1L, ME, PEER, 10L, 2, 5),
                conversation(2L, PEER, ME, 20L, 9, 4));

        List<ConversationView> views = service.list(ME, 1, 20).getRecords();

        assertThat(views).hasSize(2);
        assertThat(views.get(0).getPeerId()).isEqualTo(PEER);
        assertThat(views.get(0).getUnreadCount()).isEqualTo(2);
        assertThat(views.get(1).getPeerId()).isEqualTo(PEER);
        assertThat(views.get(1).getUnreadCount()).isEqualTo(4);
        // 归一化存法不外泄：调用方永远不知道自己是 A 还是 B
        assertThat(views).allSatisfy(view -> assertThat(view.getConversationId()).isNotNull());
    }

    @Test
    @DisplayName("分页 size 夹到 100，current 至少是 1")
    void listClampsThePage() {
        Page<ConversationView> page = service.list(ME, 0, 100_000);

        assertThat(page.getSize()).isEqualTo(100L);
        assertThat(page.getCurrent()).isEqualTo(1L);
    }

    @Test
    @DisplayName("整页摘要一次查完，不是一行一次（N+1）")
    void listFetchesAllPreviewsInOneQuery() {
        pageRecords = List.of(
                conversation(1L, ME, PEER, 10L, 0, 0),
                conversation(2L, PEER, ME, 20L, 0, 0));

        List<ConversationView> views = service.list(ME, 1, 20).getRecords();

        assertThat(previewWrappers).hasSize(1);
        assertThat(previewWrappers.get(0).getCustomSqlSegment())
                .contains("id IN")
                .contains("status =");
        assertThat(views).allSatisfy(view -> assertThat(view.getLastMsgId()).isNotNull());
    }

    @Test
    @DisplayName("最后一条消息被删了就把摘要留空，删掉的内容不该还挂在会话卡片上")
    void listLeavesTheContentNullWhenTheLastMessageWasDeleted() {
        pageRecords = List.of(conversation(1L, ME, PEER, 10L, 0, 0));

        List<ConversationView> views = service.list(ME, 1, 20).getRecords();

        assertThat(views).singleElement().satisfies(view -> {
            assertThat(view.getLastMsgId()).isEqualTo(10L);
            assertThat(view.getLastMsgContent()).isNull();
        });
    }

    @Test
    @DisplayName("摘要只带出还活着的消息，且正文被截到 60 字")
    void listTruncatesThePreview() {
        pageRecords = List.of(conversation(1L, ME, PEER, 10L, 0, 0));
        when(recordMapper.selectList(any())).thenReturn(
                List.of(message(10L, "  " + "字".repeat(80) + "  ")));

        String content = service.list(ME, 1, 20).getRecords().get(0).getLastMsgContent();

        assertThat(content).isEqualTo("字".repeat(60) + "…");
    }

    @Test
    @DisplayName("还没有任何消息的会话不去查消息表")
    void listSkipsThePreviewQueryWhenNoConversationHasALastMessage() {
        pageRecords = List.of(conversation(1L, ME, PEER, null, 0, 0));

        service.list(ME, 1, 20);

        verify(recordMapper, never()).selectList(any());
    }

    @Test
    @DisplayName("会话列表要求登录")
    void listRequiresLogin() {
        assertThatThrownBy(() -> service.list(null, 1, 20))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("未登录");
        verifyNoInteractions(mapper);
    }

    // ---------- markRead ----------

    @Test
    @DisplayName("标记已读先动消息表，再按实测值同步我这一侧的会话未读")
    void markReadRecomputesMyUnreadFromTheMessageTable() {
        when(recordMapper.markRead(eq(ME), eq(3), eq(PEER), any())).thenReturn(6);
        when(recordMapper.countUnreadFrom(ME, PEER)).thenReturn(1L);

        assertThat(service.markRead(ME, PEER)).isEqualTo(6);

        // pair = (7, 42)，我是 B → 同步 B 侧，且同步的是实测的剩余值而不是硬清 0
        verify(mapper).syncUnreadB(PEER, ME, 1L);
        verify(mapper, never()).syncUnreadA(anyLong(), anyLong(), anyLong());
    }

    @Test
    @DisplayName("我是 id 较小的那一方时，同步的是 A 侧")
    void markReadSyncsTheASideWhenIHaveTheSmallerId() {
        when(recordMapper.markRead(eq(3L), eq(3), eq(PEER), any())).thenReturn(2);
        when(recordMapper.countUnreadFrom(3L, PEER)).thenReturn(0L);

        service.markRead(3L, PEER);

        verify(mapper).syncUnreadA(3L, PEER, 0L);
        verify(mapper, never()).syncUnreadB(anyLong(), anyLong(), anyLong());
    }

    @Test
    @DisplayName("即使一条都没标上也要同步：会话上的红点和消息表长期打架比慢一点更糟")
    void markReadStillSyncsWhenNothingWasMarked() {
        when(recordMapper.markRead(eq(ME), eq(3), eq(PEER), any())).thenReturn(0);
        when(recordMapper.countUnreadFrom(ME, PEER)).thenReturn(0L);

        assertThat(service.markRead(ME, PEER)).isZero();

        verify(mapper).syncUnreadB(PEER, ME, 0L);
    }

    @Test
    @DisplayName("不能和自己开会话")
    void markReadRejectsASelfConversation() {
        assertThatThrownBy(() -> service.markRead(ME, ME))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("不能和自己开会话");
        verifyNoInteractions(mapper, recordMapper);
    }

    @Test
    @DisplayName("非法的 peerId 被拒绝")
    void markReadRejectsAnInvalidPeer() {
        assertThatThrownBy(() -> service.markRead(ME, 0L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("peerId");
        assertThatThrownBy(() -> service.markRead(ME, null))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("peerId");
    }

    @Test
    @DisplayName("标记已读要求登录")
    void markReadRequiresLogin() {
        assertThatThrownBy(() -> service.markRead(null, PEER))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("未登录");
    }

    // ---------- clearAllUnread / countWithUnread ----------

    @Test
    @DisplayName("私信全部已读时，我在 A 侧和 B 侧的未读都要清")
    void clearAllUnreadResetsBothSides() {
        service.clearAllUnread(ME);

        verify(mapper).clearUnreadA(ME);
        verify(mapper).clearUnreadB(ME);
    }

    @Test
    @DisplayName("清空未读要求登录")
    void clearAllUnreadRequiresLogin() {
        assertThatThrownBy(() -> service.clearAllUnread(null))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("未登录");
        verifyNoInteractions(mapper);
    }

    @Test
    @DisplayName("有未读的会话段数直接透传，要求登录")
    void countWithUnreadPassesThrough() {
        when(mapper.countWithUnread(ME)).thenReturn(3L);

        assertThat(service.countWithUnread(ME)).isEqualTo(3L);
        assertThatThrownBy(() -> service.countWithUnread(null))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("未登录");
    }
}
