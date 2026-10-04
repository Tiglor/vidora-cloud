package org.tiglor.interact.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.tiglor.common.core.ApiResult;
import org.tiglor.common.core.security.UserContext;
import org.tiglor.interact.dto.ActionCounts;
import org.tiglor.interact.dto.ActionRequest;
import org.tiglor.interact.entity.InteractAction;
import org.tiglor.interact.enums.ActionType;
import org.tiglor.interact.enums.TargetType;
import org.tiglor.interact.service.InteractActionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 点赞 / 收藏 / 分享。
 * <p>
 * 这些接口只要登录就能调，不额外挂权限位：它们是普通用户的基本操作，
 * 加权限只会让「谁能点赞」变成一件要配菜单的事。
 * </p>
 */
@RestController
@RequestMapping("/actions")
@RequiredArgsConstructor
public class InteractActionController {

    private final InteractActionService actionService;

    /** 设置为点赞/收藏/分享或其取消状态。幂等：重复提交同一状态不会重复计数 */
    @PutMapping
    public ApiResult<ActionCounts> setActive(@Valid @RequestBody ActionRequest request) {
        return ApiResult.ok(actionService.setActive(
                TargetType.of(request.getTargetType()),
                request.getTargetId(),
                ActionType.of(request.getActionType()),
                request.isActive(),
                UserContext.getUserId()));
    }

    /** 某对象的计数与当前用户的动作状态 */
    @GetMapping("/counts")
    public ApiResult<ActionCounts> counts(@RequestParam String targetType, @RequestParam Long targetId) {
        return ApiResult.ok(actionService.counts(
                TargetType.of(targetType), targetId, UserContext.getUserId()));
    }

    /** 我的收藏，按收藏时间倒序 */
    @GetMapping("/favorites")
    public ApiResult<Page<InteractAction>> myFavorites(@RequestParam(defaultValue = "1") long current,
                                                       @RequestParam(defaultValue = "20") long size) {
        return ApiResult.ok(actionService.myFavorites(UserContext.getUserId(), current, size));
    }
}
