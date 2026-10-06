package org.tiglor.message.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tiglor.common.core.BizException;
import org.tiglor.common.core.ResultCode;
import org.tiglor.message.dto.ConversationView;
import org.tiglor.message.entity.MessageConversation;
import org.tiglor.message.entity.MessageRecord;
import org.tiglor.message.enums.MsgType;
import org.tiglor.message.mapper.MessageConversationMapper;
import org.tiglor.message.mapper.MessageRecordMapper;
import org.tiglor.message.service.ConversationService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationServiceImpl extends ServiceImpl<MessageConversationMapper, MessageConversation>
        implements ConversationService {

    private static final long MAX_PAGE_SIZE = 100L;

    /** 会话卡片上「最后一条消息」的摘要长度，整段正文塞进列表接口纯属浪费带宽 */
    private static final int PREVIEW_LENGTH = 60;

    private final MessageRecordMapper messageRecordMapper;

    @Override
    public void touchOnSend(Long senderId, Long receiverId, Long lastMsgId, LocalDateTime lastMsgTime) {
        Pair pair = Pair.of(senderId, receiverId);
        // 收信方是 A 还是 B 决定了增量落在哪一列，另一列恒为 0
        boolean receiverIsA = Objects.equals(pair.a, receiverId);
        baseMapper.touchOnSend(pair.a, pair.b, lastMsgId, lastMsgTime,
                receiverIsA ? 1 : 0, receiverIsA ? 0 : 1);
    }

    @Override
    public Page<ConversationView> list(Long userId, long current, long size) {
        requireUserId(userId);
        Page<MessageConversation> page = lambdaQuery()
                // 归一化存储之后「我」可能是 A 也可能是 B，这个 OR 躲不掉
                .and(w -> w.eq(MessageConversation::getUserIdA, userId)
                        .or(o -> o.eq(MessageConversation::getUserIdB, userId)))
                .orderByDesc(MessageConversation::getLastMsgTime)
                // 兜底的唯一排序键：同一秒里建的多段会话，翻页时会重复出现或被跳过
                .orderByDesc(MessageConversation::getId)
                .page(new Page<>(Math.max(current, 1), clampSize(size)));

        Page<ConversationView> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        result.setRecords(page.getRecords().stream()
                .map(row -> toView(row, userId))
                .collect(Collectors.toList()));
        attachLastMessageContent(result.getRecords());
        return result;
    }

    @Override
    @Transactional
    public int markRead(Long userId, Long peerId) {
        requireUserId(userId);
        if (peerId == null || peerId <= 0) {
            throw new BizException(ResultCode.VALIDATE_FAILED, "peerId 非法");
        }
        if (Objects.equals(userId, peerId)) {
            throw new BizException(ResultCode.VALIDATE_FAILED, "不能和自己开会话");
        }
        int marked = messageRecordMapper.markRead(userId, MsgType.PRIVATE.getCode(), peerId, LocalDateTime.now());
        syncUnread(userId, peerId);
        return marked;
    }

    @Override
    @Transactional
    public void clearAllUnread(Long userId) {
        requireUserId(userId);
        baseMapper.clearUnreadA(userId);
        baseMapper.clearUnreadB(userId);
    }

    @Override
    public long countWithUnread(Long userId) {
        requireUserId(userId);
        return baseMapper.countWithUnread(userId);
    }

    /**
     * 把我在这一段会话里的未读数同步成 {@code message_record} 上的实测值。
     * <p>
     * 必须在 {@link #markRead} 标记完消息「之后」调用，且和它同一个事务：
     * 顺序反了或不在一个事务里，中间插进来的新私信会被清掉未读，
     * 于是那条消息在收件箱里是未读、在会话列表上却顶不出红点。
     * </p>
     */
    private void syncUnread(Long userId, Long peerId) {
        long remaining = messageRecordMapper.countUnreadFrom(userId, peerId);
        Pair pair = Pair.of(userId, peerId);
        if (Objects.equals(pair.a, userId)) {
            baseMapper.syncUnreadA(pair.a, pair.b, remaining);
        } else {
            baseMapper.syncUnreadB(pair.a, pair.b, remaining);
        }
    }

    /**
     * 一次查询补齐整页会话的「最后一条消息」摘要。
     * <p>
     * 会话表只存了 last_msg_id，逐行去查消息就是 N+1（一页 20 段会话 = 20 次往返）。
     * 已删除的消息不参与摘要：删了的内容不该还挂在会话卡片上，此时 lastMsgContent 为 null，
     * 由前端回退到只显示时间。
     * </p>
     */
    private void attachLastMessageContent(List<ConversationView> views) {
        List<Long> ids = views.stream()
                .map(ConversationView::getLastMsgId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return;
        }
        Map<Long, MessageRecord> byId = messageRecordMapper.selectList(
                        Wrappers.<MessageRecord>lambdaQuery()
                                .in(MessageRecord::getId, ids)
                                .eq(MessageRecord::getStatus, 1)).stream()
                .collect(Collectors.toMap(MessageRecord::getId, Function.identity()));
        views.forEach(view -> {
            MessageRecord last = byId.get(view.getLastMsgId());
            view.setLastMsgContent(last == null ? null : preview(last.getContent()));
        });
    }

    private static ConversationView toView(MessageConversation row, Long userId) {
        boolean iAmA = Objects.equals(row.getUserIdA(), userId);
        ConversationView view = new ConversationView();
        view.setConversationId(row.getId());
        view.setPeerId(iAmA ? row.getUserIdB() : row.getUserIdA());
        view.setLastMsgId(row.getLastMsgId());
        view.setLastMsgTime(row.getLastMsgTime());
        view.setUnreadCount(iAmA ? row.getUnreadCountA() : row.getUnreadCountB());
        return view;
    }

    private static String preview(String content) {
        if (content == null) {
            return null;
        }
        String trimmed = content.trim();
        return trimmed.length() <= PREVIEW_LENGTH ? trimmed : trimmed.substring(0, PREVIEW_LENGTH) + "…";
    }

    private static long clampSize(long size) {
        return Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
    }

    private static void requireUserId(Long userId) {
        if (userId == null) {
            throw new BizException(ResultCode.UNAUTHORIZED, "未登录");
        }
    }

    /**
     * 归一化后的会话双方：{@code a} 恒为 id 较小者。
     * <p>
     * 这是 {@code uk_conversation(user_id_a, user_id_b)} 能真正唯一的前提。
     * 不归一化的话「1 找 2」和「2 找 1」各建一行，同一段对话被劈成两半，
     * 两边各看各的未读数，谁也不知道对面已经回过话了。
     * </p>
     */
    private record Pair(Long a, Long b) {

        static Pair of(Long one, Long other) {
            if (one == null || other == null) {
                throw new BizException(ResultCode.VALIDATE_FAILED, "会话双方都不能为空");
            }
            return one <= other ? new Pair(one, other) : new Pair(other, one);
        }
    }
}
