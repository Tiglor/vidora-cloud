package org.tiglor.message.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.tiglor.common.core.ApiResult;
import org.tiglor.common.core.security.UserContext;
import org.tiglor.message.dto.ConversationView;
import org.tiglor.message.service.ConversationService;

/**
 * 私信会话列表。
 * <p>
 * 会话行按 (min, max) 归一化存储，但接口只讲「我」和「对面」——
 * 调用方不需要知道自己在这一行里是 user_id_a 还是 user_id_b。
 * </p>
 */
@RestController
@RequestMapping("/conversations")
@RequiredArgsConstructor
public class ConversationController {

    private final ConversationService conversationService;

    /** 我的会话列表，按最后一条消息时间倒序，每行带摘要和未读数 */
    @GetMapping
    public ApiResult<Page<ConversationView>> list(@RequestParam(defaultValue = "1") long current,
                                                  @RequestParam(defaultValue = "20") long size) {
        return ApiResult.ok(conversationService.list(UserContext.getUserId(), current, size));
    }

    /** 把与某人的私信全部标记已读，返回被标记的条数 */
    @PutMapping("/{peerId}/read")
    public ApiResult<Integer> markRead(@PathVariable Long peerId) {
        return ApiResult.ok(conversationService.markRead(UserContext.getUserId(), peerId));
    }
}
