package org.tiglor.message.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.tiglor.common.core.ApiResult;
import org.tiglor.common.core.security.UserContext;
import org.tiglor.message.dto.MessageView;
import org.tiglor.message.dto.NotifyRequest;
import org.tiglor.message.dto.PrivateMessageRequest;
import org.tiglor.message.dto.UnreadSummary;
import org.tiglor.message.service.MessageService;

/**
 * 消息收件箱。
 * <p>
 * 收信人一律取 {@link UserContext#getUserId()}，不接受请求参数指定——原先那个把
 * {@code IService#page} 直接暴露出去的控制器等于让任何登录用户翻遍全站的私信。
 * </p>
 */
@RestController
@RequestMapping("/messages")
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;

    /** 我的收件箱，msgType 省略时返回全部类型 */
    @GetMapping
    public ApiResult<Page<MessageView>> inbox(@RequestParam(required = false) Integer msgType,
                                              @RequestParam(defaultValue = "1") long current,
                                              @RequestParam(defaultValue = "20") long size) {
        return ApiResult.ok(messageService.inbox(UserContext.getUserId(), msgType, current, size));
    }

    /** 三类消息各自的未读数 + 还有未读的会话段数，给客户端红点用 */
    @GetMapping("/unread")
    public ApiResult<UnreadSummary> unreadSummary() {
        return ApiResult.ok(messageService.unreadSummary(UserContext.getUserId()));
    }

    /** 与某人的私信往来（双向）。收件箱只有「我收到的」，发出去的从这里看 */
    @GetMapping("/thread/{peerId}")
    public ApiResult<Page<MessageView>> thread(@PathVariable Long peerId,
                                               @RequestParam(defaultValue = "1") long current,
                                               @RequestParam(defaultValue = "20") long size) {
        return ApiResult.ok(messageService.thread(UserContext.getUserId(), peerId, current, size));
    }

    /**
     * 发系统通知 / 互动消息。
     * <p>
     * 挂 {@code message:send} 权限：这个接口能往任何人收件箱里塞一条「系统」消息，
     * 对普通用户开放就等于开放了伪造官方通知。将来的正常调用方是 MQ 消费者
     * （被点赞、被评论时由 interact-service 发事件），届时这条 HTTP 路径留给管理后台。
     * </p>
     */
    @PostMapping("/notify")
    @PreAuthorize("hasAuthority('message:send')")
    public ApiResult<MessageView> sendNotification(@Valid @RequestBody NotifyRequest request) {
        return ApiResult.ok(messageService.sendNotification(request));
    }

    /** 发私信。发信人是当前登录用户，客户端改不了 */
    @PostMapping("/private")
    public ApiResult<MessageView> sendPrivateMessage(@Valid @RequestBody PrivateMessageRequest request) {
        return ApiResult.ok(messageService.sendPrivateMessage(request, UserContext.getUserId()));
    }

    /**
     * 标记已读，返回被标记的条数。
     * <p>
     * 私信传 peerId 只标记这一段会话，不传则整个私信收件箱全标已读；
     * 系统通知和互动消息不接受 peerId。
     * </p>
     */
    @PutMapping("/read")
    public ApiResult<Integer> markRead(@RequestParam Integer msgType,
                                       @RequestParam(required = false) Long peerId) {
        return ApiResult.ok(messageService.markRead(UserContext.getUserId(), msgType, peerId));
    }

    /** 删除（隐藏）一条消息，发信人或收信人都可以 */
    @DeleteMapping("/{id}")
    public ApiResult<Void> delete(@PathVariable Long id) {
        messageService.delete(id, UserContext.getUserId());
        return ApiResult.ok();
    }
}
