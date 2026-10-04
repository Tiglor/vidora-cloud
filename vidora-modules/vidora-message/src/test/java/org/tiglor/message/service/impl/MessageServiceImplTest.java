package org.tiglor.message.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.tiglor.common.core.BizException;
import org.tiglor.common.test.MpTestSupport;
import org.tiglor.message.dto.MessageView;
import org.tiglor.message.dto.MsgTypeCount;
import org.tiglor.message.dto.NotifyRequest;
import org.tiglor.message.dto.PrivateMessageRequest;
import org.tiglor.message.dto.UnreadSummary;
import org.tiglor.message.entity.MessageRecord;
import org.tiglor.message.mapper.MessageRecordMapper;
import org.tiglor.message.service.ConversationService;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 收件箱的归属边界、三类消息的发送资格、已读与删除。
 * <p>
 * 这个模块最要紧的性质是「任何查询都以当前登录用户为收信人」——原先那个直接暴露
 * {@code IService#page} 的控制器能让任何登录用户翻遍全站私信，这里把边界钉死。
 * </p>
 */
class MessageServiceImplTest {

    private static final long ME = 42L;
    private static final long PEER = 7L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 3, 12, 0);

    MessageRecordMapper mapper;
    ConversationService conversationService;
    MessageServiceImpl service;

    private final List<Wrapper<MessageRecord>> pageWrappers = new ArrayList<>();
    private final List<LambdaUpdateWrapper<MessageRecord>> updateWrappers = new ArrayList<>();
    private List<MessageRecord> pageRecords = List.of();

    @BeforeAll
    static void initTableInfo() {
        MpTestSupport.initTableInfo(MessageRecord.class);
    }

    @BeforeEach
    void setUp() {
        mapper = mock(MessageRecordMapper.class);
        conversationService = mock(ConversationService.class);
        service = new MessageServiceImpl(conversationService);
        MpTestSupport.injectMapper(service, mapper, MessageRecordMapper.class);

        // 模拟 MyBatis-Plus 的自增主键回填与 AutoFillHandler 的 createTime 填充
        when(mapper.insert(any(MessageRecord.class))).thenAnswer(invocation -> {
            MessageRecord record = invocation.getArgument(0);
            record.setId(555L);
            record.setCreateTime(NOW);
            return 1;
        });
        when(mapper.selectPage(any(), any())).thenAnswer(invocation -> {
            pageWrappers.add(invocation.getArgument(1));
            Page<MessageRecord> page = invocation.getArgument(0);
            page.setRecords(pageRecords);
            page.setTotal(pageRecords.size());
            return page;
        });
        when(mapper.update(any(), any())).thenAnswer(invocation -> {
            updateWrappers.add(invocation.getArgument(1));
            return 1;
        });
        when(mapper.countUnreadByType(anyLong())).thenReturn(List.of());
    }

    private String pageSql() {
        assertThat(pageWrappers).as("应该恰好发起一次分页查询").hasSize(1);
        return pageWrappers.get(0).getCustomSqlSegment();
    }

    private static MessageRecord record(long id, int msgType, long senderId, long receiverId) {
        MessageRecord record = new MessageRecord();
        record.setId(id);
        record.setMsgType(msgType);
        record.setSenderId(senderId);
        record.setReceiverId(receiverId);
        record.setContent("内容" + id);
        record.setIsRead(0);
        record.setStatus(1);
        record.setCreateTime(NOW);
        return record;
    }

    private static MsgTypeCount unread(int msgType, long total) {
        MsgTypeCount row = new MsgTypeCount();
        row.setMsgType(msgType);
        row.setTotal(total);
        return row;
    }

    // ---------- inbox ----------

    @Test
    @DisplayName("收件箱永远以当前登录用户为收信人，且过滤掉已删除的")
    void inboxIsAlwaysScopedToTheLoggedInUser() {
        service.inbox(ME, null, 1, 20);

        assertThat(pageSql())
                .contains("receiver_id =")
                .contains("status =")
                .contains("ORDER BY id DESC");
    }

    @Test
    @DisplayName("不传 msgType 时返回全部类型，传了就只返回那一类")
    void inboxFiltersByTypeOnlyWhenAsked() {
        service.inbox(ME, null, 1, 20);
        assertThat(pageSql()).doesNotContain("msg_type");

        pageWrappers.clear();
        service.inbox(ME, 2, 1, 20);
        assertThat(pageSql()).contains("msg_type =");
    }

    @Test
    @DisplayName("未知的 msgType 直接拒绝，而不是静默返回全部")
    void inboxRejectsAnUnknownType() {
        assertThatThrownBy(() -> service.inbox(ME, 9, 1, 20))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("未知的消息类型");
    }

    @Test
    @DisplayName("收件箱要求登录，分页 size 夹到 100")
    void inboxRequiresLoginAndClampsThePage() {
        assertThatThrownBy(() -> service.inbox(null, null, 1, 20))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("未登录");
        assertThat(service.inbox(ME, null, 0, 100_000).getSize()).isEqualTo(100L);
    }

    @Test
    @DisplayName("实体不外泄：返回的是 MessageView，status 这种存储层字段不出现")
    void inboxProjectsOntoTheView() {
        pageRecords = List.of(record(1L, 3, PEER, ME));

        List<MessageView> views = service.inbox(ME, null, 1, 20).getRecords();

        assertThat(views).hasSize(1);
        assertThat(views.get(0).getContent()).isEqualTo("内容1");
        assertThat(views.get(0).getRead()).isFalse();
        assertThat(views.get(0).getSenderId()).isEqualTo(PEER);
    }

    // ---------- unread summary ----------

    @Test
    @DisplayName("未读汇总把分组结果摊到三类上，缺的类型补 0，total 是三者之和")
    void unreadSummaryMapsTheGroupedRows() {
        when(mapper.countUnreadByType(ME)).thenReturn(List.of(unread(1, 4L), unread(3, 9L)));
        when(conversationService.countWithUnread(ME)).thenReturn(2L);

        UnreadSummary summary = service.unreadSummary(ME);

        assertThat(summary.getSystemCount()).isEqualTo(4L);
        assertThat(summary.getInteractCount()).isZero();
        assertThat(summary.getPrivateCount()).isEqualTo(9L);
        assertThat(summary.getTotal()).isEqualTo(13L);
        assertThat(summary.getConversationCount()).isEqualTo(2L);
    }

    @Test
    @DisplayName("未读汇总要求登录")
    void unreadSummaryRequiresLogin() {
        assertThatThrownBy(() -> service.unreadSummary(null))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("未登录");
    }

    // ---------- send ----------

    @Test
    @DisplayName("通知接口不能用来发私信：私信要建会话、要算双方未读，走这里只会留下孤儿消息")
    void sendNotificationRejectsThePrivateType() {
        NotifyRequest request = new NotifyRequest();
        request.setMsgType(3);
        request.setReceiverId(PEER);
        request.setContent("hi");

        assertThatThrownBy(() -> service.sendNotification(request))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("私信");
        verify(mapper, never()).insert(any(MessageRecord.class));
    }

    @Test
    @DisplayName("系统通知的发件人恒为 0，新消息一律未读、状态正常")
    void sendNotificationIsSentByTheSystem() {
        NotifyRequest request = new NotifyRequest();
        request.setMsgType(1);
        request.setReceiverId(PEER);
        request.setContent("  你的视频已通过审核  ");

        MessageView view = service.sendNotification(request);

        assertThat(view.getSenderId()).isZero();
        assertThat(view.getReceiverId()).isEqualTo(PEER);
        assertThat(view.getContent()).isEqualTo("你的视频已通过审核");
        assertThat(view.getRead()).isFalse();
        verifyNoInteractions(conversationService);
    }

    @Test
    @DisplayName("互动消息也走通知接口，但类型是 2")
    void sendNotificationCarriesTheInteractType() {
        NotifyRequest request = new NotifyRequest();
        request.setMsgType(2);
        request.setReceiverId(PEER);
        request.setContent("有人赞了你的视频");

        assertThat(service.sendNotification(request).getMsgType()).isEqualTo(2);
    }

    @Test
    @DisplayName("不能给自己发私信")
    void sendPrivateMessageRejectsSendingToYourself() {
        PrivateMessageRequest request = new PrivateMessageRequest();
        request.setReceiverId(ME);
        request.setContent("自言自语");

        assertThatThrownBy(() -> service.sendPrivateMessage(request, ME))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("不能给自己发私信");
    }

    @Test
    @DisplayName("私信先落库再推进会话：会话要引用消息的 id 和创建时间")
    void sendPrivateMessagePushesTheConversationAfterTheRowIsInserted() {
        PrivateMessageRequest request = new PrivateMessageRequest();
        request.setReceiverId(PEER);
        request.setContent("在吗");

        MessageView view = service.sendPrivateMessage(request, ME);

        assertThat(view.getMsgType()).isEqualTo(3);
        assertThat(view.getSenderId()).isEqualTo(ME);
        InOrder order = inOrder(mapper, conversationService);
        order.verify(mapper).insert(any(MessageRecord.class));
        order.verify(conversationService).touchOnSend(ME, PEER, 555L, NOW);
    }

    @Test
    @DisplayName("发私信要求登录，且发件人由服务端指定")
    void sendPrivateMessageRequiresLogin() {
        PrivateMessageRequest request = new PrivateMessageRequest();
        request.setReceiverId(PEER);
        request.setContent("在吗");

        assertThatThrownBy(() -> service.sendPrivateMessage(request, null))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("未登录");
    }

    // ---------- thread ----------

    @Test
    @DisplayName("私信会话是双向的：我发给他的和他发给我的都在同一条时间线上")
    void threadCoversBothDirections() {
        service.thread(ME, PEER, 1, 20);

        assertThat(pageSql())
                .contains("msg_type =")
                .contains("receiver_id =")
                .contains("sender_id =")
                .contains("OR")
                .contains("ORDER BY id DESC");
    }

    @Test
    @DisplayName("非法的 peerId 被拒绝")
    void threadRejectsAnInvalidPeer() {
        assertThatThrownBy(() -> service.thread(ME, 0L, 1, 20))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("peerId");
    }

    // ---------- mark read ----------

    @Test
    @DisplayName("系统通知标记已读只动消息表，不碰会话")
    void markReadOnNotificationsDoesNotTouchConversations() {
        when(mapper.markRead(eq(ME), eq(1), isNull(), any())).thenReturn(4);

        assertThat(service.markRead(ME, 1, null)).isEqualTo(4);

        verifyNoInteractions(conversationService);
    }

    @Test
    @DisplayName("非私信类型不接受 peerId：那会静默忽略一个客户端以为生效了的过滤条件")
    void markReadRejectsPeerIdForNonPrivateTypes() {
        assertThatThrownBy(() -> service.markRead(ME, 1, PEER))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("peerId 只对私信有意义");
    }

    @Test
    @DisplayName("标记单段私信会话已读交给会话服务，未读数由它重算")
    void markReadOnASingleConversationDelegates() {
        when(conversationService.markRead(ME, PEER)).thenReturn(3);

        assertThat(service.markRead(ME, 3, PEER)).isEqualTo(3);

        verify(mapper, never()).markRead(anyLong(), anyInt(), any(), any());
    }

    @Test
    @DisplayName("私信全部已读时，所有会话的未读数一并清零")
    void markReadOnTheWholePrivateInboxAlsoClearsEveryConversation() {
        when(mapper.markRead(eq(ME), eq(3), isNull(), any())).thenReturn(8);

        assertThat(service.markRead(ME, 3, null)).isEqualTo(8);

        InOrder order = inOrder(mapper, conversationService);
        order.verify(mapper).markRead(eq(ME), eq(3), isNull(), any());
        order.verify(conversationService).clearAllUnread(ME);
    }

    @Test
    @DisplayName("未知的消息类型在标记已读时就被挡下")
    void markReadRejectsAnUnknownType() {
        assertThatThrownBy(() -> service.markRead(ME, 9, null))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("未知的消息类型");
    }

    // ---------- delete ----------

    @Test
    @DisplayName("无关的第三方不能删别人的消息")
    void deleteByAStrangerIsForbidden() {
        doReturn(record(1L, 3, PEER, 99L)).when(mapper).selectById(1L);

        assertThatThrownBy(() -> service.delete(1L, ME))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("只能删除自己收到的或发出的消息");
        verify(mapper, never()).update(any(), any());
    }

    @Test
    @DisplayName("收信人可以删，发信人也可以删")
    void deleteByEitherParticipantIsAllowed() {
        doReturn(record(1L, 3, PEER, ME)).when(mapper).selectById(1L);
        service.delete(1L, ME);

        doReturn(record(2L, 3, ME, PEER)).when(mapper).selectById(2L);
        service.delete(2L, ME);

        assertThat(updateWrappers).hasSize(2);
    }

    @Test
    @DisplayName("删除是隐藏（status=0）而不是物理删行，且写成条件更新")
    void deleteHidesTheMessageWithAConditionalUpdate() {
        doReturn(record(1L, 1, 0L, ME)).when(mapper).selectById(1L);

        service.delete(1L, ME);

        assertThat(updateWrappers).hasSize(1);
        LambdaUpdateWrapper<MessageRecord> wrapper = updateWrappers.get(0);
        assertThat(wrapper.getSqlSet()).contains("status=");
        assertThat(wrapper.getCustomSqlSegment()).contains("id =").contains("status <>");
    }

    @Test
    @DisplayName("已经删过的消息再删返回 404，而不是假装成功")
    void deleteAnAlreadyHiddenMessageIsNotFound() {
        MessageRecord hidden = record(1L, 1, 0L, ME);
        hidden.setStatus(0);
        doReturn(hidden).when(mapper).selectById(1L);

        assertThatThrownBy(() -> service.delete(1L, ME))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("消息不存在");
    }

    @Test
    @DisplayName("删一条不存在的消息返回 404")
    void deleteMissingMessageIsNotFound() {
        doReturn(null).when(mapper).selectById(999L);

        assertThatThrownBy(() -> service.delete(999L, ME))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("消息不存在");
    }

    @Test
    @DisplayName("删除要求登录")
    void deleteRequiresLogin() {
        assertThatThrownBy(() -> service.delete(1L, null))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("未登录");
    }
}
