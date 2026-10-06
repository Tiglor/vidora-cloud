package org.tiglor.message.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.tiglor.message.dto.MessageView;
import org.tiglor.message.dto.NotifyRequest;
import org.tiglor.message.dto.PrivateMessageRequest;
import org.tiglor.message.dto.UnreadSummary;

/**
 * 消息收件箱。
 * <p>
 * 所有查询都以「当前登录用户是收信人」为前提，接口上不存在「看别人的消息」这条路——
 * 原先那个直接暴露 {@code IService#page} 的控制器等于把全站的私信摊开给任何登录用户。
 * </p>
 */
public interface MessageService {

    /**
     * 我的收件箱，按时间倒序。
     *
     * @param msgType 为 null 时返回全部类型
     */
    Page<MessageView> inbox(Long receiverId, Integer msgType, long current, long size);

    /** 三类消息各自的未读数 + 还有未读的会话段数 */
    UnreadSummary unreadSummary(Long userId);

    /**
     * 发一条系统通知或互动消息。
     * <p>
     * 只给内部调用方用（管理后台，或将来的 MQ 消费者），不开放给普通用户，
     * 否则谁都能给别人伪造一条「你的视频已被下架」。
     * </p>
     */
    MessageView sendNotification(NotifyRequest request);

    /** 发一条私信，同时推进会话。发信人取当前登录用户，不接受客户端指定 */
    MessageView sendPrivateMessage(PrivateMessageRequest request, Long senderId);

    /**
     * 与某人的私信往来（双向），按时间倒序。
     * <p>
     * 收件箱只包含「我收到的」，发出去的私信只能从这里看到。
     * </p>
     */
    Page<MessageView> thread(Long userId, Long peerId, long current, long size);

    /**
     * 标记已读。
     *
     * @param msgType 消息类型，必填
     * @param peerId  仅私信有意义：非空时只标记这一段会话，为空时整个私信收件箱全标已读
     * @return 被标记的消息条数
     */
    int markRead(Long userId, Integer msgType, Long peerId);

    /**
     * 删除（隐藏）一条消息，发信人或收信人都可以。
     * <p>
     * <b>已知局限</b>：一条消息只有一行，没有「按接收方分别可见」的表，
     * 所以对任何一方删除都会让它从双方的列表里消失。要做到各自独立删除，
     * 得引入 message_receiver 关联表，见 .code/ARCHITECTURE.md 的待完成项。
     * </p>
     */
    void delete(Long messageId, Long userId);
}
