package org.tiglor.message.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tiglor.common.core.BizException;
import org.tiglor.common.core.ResultCode;
import org.tiglor.message.dto.MessageView;
import org.tiglor.message.dto.MsgTypeCount;
import org.tiglor.message.dto.NotifyRequest;
import org.tiglor.message.dto.PrivateMessageRequest;
import org.tiglor.message.dto.UnreadSummary;
import org.tiglor.message.entity.MessageRecord;
import org.tiglor.message.enums.MsgType;
import org.tiglor.message.mapper.MessageRecordMapper;
import org.tiglor.message.service.ConversationService;
import org.tiglor.message.service.MessageService;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MessageServiceImpl extends ServiceImpl<MessageRecordMapper, MessageRecord> implements MessageService {

    /** sender_id = 0 表示系统发的，DDL 上的默认值 */
    private static final long SYSTEM_SENDER = 0L;

    private static final int UNREAD = 0;
    private static final int STATUS_NORMAL = 1;
    private static final int STATUS_DELETED = 0;

    private static final long MAX_PAGE_SIZE = 100L;

    private final ConversationService conversationService;

    @Override
    public Page<MessageView> inbox(Long receiverId, Integer msgType, long current, long size) {
        requireUserId(receiverId);
        MsgType filter = msgType == null ? null : MsgType.of(msgType);
        Page<MessageRecord> page = lambdaQuery()
                .eq(MessageRecord::getReceiverId, receiverId)
                .eq(MessageRecord::getStatus, STATUS_NORMAL)
                .eq(filter != null, MessageRecord::getMsgType, filter == null ? null : filter.getCode())
                .orderByDesc(MessageRecord::getId)
                .page(new Page<>(Math.max(current, 1), clampSize(size)));
        return toViewPage(page);
    }

    @Override
    public UnreadSummary unreadSummary(Long userId) {
        requireUserId(userId);
        Map<Integer, Long> byType = baseMapper.countUnreadByType(userId).stream()
                .collect(Collectors.toMap(MsgTypeCount::getMsgType, MsgTypeCount::getTotal));

        UnreadSummary summary = new UnreadSummary();
        summary.setSystemCount(byType.getOrDefault(MsgType.SYSTEM.getCode(), 0L));
        summary.setInteractCount(byType.getOrDefault(MsgType.INTERACT.getCode(), 0L));
        summary.setPrivateCount(byType.getOrDefault(MsgType.PRIVATE.getCode(), 0L));
        summary.setTotal(summary.getSystemCount() + summary.getInteractCount() + summary.getPrivateCount());
        summary.setConversationCount(conversationService.countWithUnread(userId));
        return summary;
    }

    @Override
    public MessageView sendNotification(NotifyRequest request) {
        MsgType type = MsgType.of(request.getMsgType());
        if (type.isPrivate()) {
            // 私信要建会话、要算双方未读，走的是另一条路径；从这里发只会留下一条没有会话的孤儿私信
            throw new BizException(ResultCode.VALIDATE_FAILED, "这个接口只发系统通知和互动消息，私信请走 /messages/private");
        }
        MessageRecord record = newRecord(type, SYSTEM_SENDER, request.getReceiverId(),
                request.getContent(), request.getExtra());
        save(record);
        return toView(record);
    }

    @Override
    @Transactional
    public MessageView sendPrivateMessage(PrivateMessageRequest request, Long senderId) {
        requireUserId(senderId);
        if (Objects.equals(senderId, request.getReceiverId())) {
            throw new BizException(ResultCode.VALIDATE_FAILED, "不能给自己发私信");
        }
        MessageRecord record = newRecord(MsgType.PRIVATE, senderId, request.getReceiverId(),
                request.getContent(), request.getExtra());
        save(record);
        // 消息行落库之后再推进会话：会话要引用它的 id 和时间，顺序反了 last_msg_id 就是 null
        conversationService.touchOnSend(senderId, request.getReceiverId(),
                record.getId(), record.getCreateTime());
        return toView(record);
    }

    @Override
    public Page<MessageView> thread(Long userId, Long peerId, long current, long size) {
        requireUserId(userId);
        if (peerId == null || peerId <= 0) {
            throw new BizException(ResultCode.VALIDATE_FAILED, "peerId 非法");
        }
        Page<MessageRecord> page = lambdaQuery()
                .eq(MessageRecord::getMsgType, MsgType.PRIVATE.getCode())
                .eq(MessageRecord::getStatus, STATUS_NORMAL)
                // 私信是双向的：我发给对方的和对方发给我的都要在同一个时间线上
                .and(w -> w.eq(MessageRecord::getReceiverId, userId).eq(MessageRecord::getSenderId, peerId)
                        .or(o -> o.eq(MessageRecord::getReceiverId, peerId).eq(MessageRecord::getSenderId, userId)))
                .orderByDesc(MessageRecord::getId)
                .page(new Page<>(Math.max(current, 1), clampSize(size)));
        return toViewPage(page);
    }

    @Override
    @Transactional
    public int markRead(Long userId, Integer msgType, Long peerId) {
        requireUserId(userId);
        MsgType type = MsgType.of(msgType);
        if (!type.isPrivate()) {
            if (peerId != null) {
                throw new BizException(ResultCode.VALIDATE_FAILED, "peerId 只对私信有意义");
            }
            return baseMapper.markRead(userId, type.getCode(), null, LocalDateTime.now());
        }
        if (peerId != null) {
            return conversationService.markRead(userId, peerId);
        }
        // 整个私信收件箱全标已读，会话上的未读数随后一并清零（同一个事务）
        int marked = baseMapper.markRead(userId, MsgType.PRIVATE.getCode(), null, LocalDateTime.now());
        conversationService.clearAllUnread(userId);
        return marked;
    }

    @Override
    public void delete(Long messageId, Long userId) {
        requireUserId(userId);
        MessageRecord record = getById(messageId);
        if (record == null || record.getStatus() != null && record.getStatus() == STATUS_DELETED) {
            throw new BizException(ResultCode.NOT_FOUND, "消息不存在：" + messageId);
        }
        boolean participant = userId.equals(record.getReceiverId())
                || (record.getSenderId() != null && userId.equals(record.getSenderId()));
        if (!participant) {
            throw new BizException(ResultCode.FORBIDDEN, "只能删除自己收到的或发出的消息");
        }
        // 条件更新：重复的删除请求第二次影响 0 行，不会把一条已被别的语义改过的行再动一次
        lambdaUpdate()
                .set(MessageRecord::getStatus, STATUS_DELETED)
                .eq(MessageRecord::getId, messageId)
                .ne(MessageRecord::getStatus, STATUS_DELETED)
                .update();
    }

    private static MessageRecord newRecord(MsgType type, long senderId, Long receiverId,
                                           String content, String extra) {
        if (receiverId == null || receiverId <= 0) {
            throw new BizException(ResultCode.VALIDATE_FAILED, "receiverId 非法");
        }
        MessageRecord record = new MessageRecord();
        record.setMsgType(type.getCode());
        record.setSenderId(senderId);
        record.setReceiverId(receiverId);
        record.setContent(content.trim());
        record.setExtra(extra);
        record.setIsRead(UNREAD);
        record.setStatus(STATUS_NORMAL);
        return record;
    }

    private static Page<MessageView> toViewPage(Page<MessageRecord> page) {
        Page<MessageView> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        result.setRecords(page.getRecords().stream().map(MessageServiceImpl::toView).collect(Collectors.toList()));
        return result;
    }

    private static MessageView toView(MessageRecord record) {
        MessageView view = new MessageView();
        view.setId(record.getId());
        view.setMsgType(record.getMsgType());
        view.setSenderId(record.getSenderId());
        view.setReceiverId(record.getReceiverId());
        view.setContent(record.getContent());
        view.setExtra(record.getExtra());
        view.setRead(record.getIsRead() != null && record.getIsRead() == 1);
        view.setReadTime(record.getReadTime());
        view.setCreateTime(record.getCreateTime());
        return view;
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
