package org.tiglor.auth.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.tiglor.auth.dto.UpdateThemeDTO;
import org.tiglor.auth.service.ProfileService;
import org.tiglor.common.core.ApiResult;
import org.tiglor.common.core.security.UserContext;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 当前登录用户的自助设置。
 * <p>
 * 与管理端的 UserController 严格分开：那边改的是「任何用户」，需要 system:user:edit 权限，
 * 且被网关按 clientKey 锁在管理端；这边只能改自己，任何端登录了都能调。
 * userId 一律取自 {@link UserContext}（网关透传的 X-User-Id），绝不接受前端传入。
 * </p>
 */
@RestController
@RequestMapping("/profile")
@RequiredArgsConstructor
public class ProfileController {

    private final ProfileService profileService;

    /**
     * 修改我的主题
     *
     * <p>覆盖式保存当前登录用户的主题标识。</p>
     * <p>
     * 服务端不校验它是不是一个已知主题：加主题包不该逼着后端改代码重新发版，认不出的 key
     * 前端渲染时会自己落回默认色。落库在 system-service 侧，本服务只挡「必须登录」这一道，
     * 未登录时报 401。成功不回读后的值，前端要确认得再查一次用户信息。
     * </p>
     */
    @PutMapping("/theme")
    public ApiResult<Void> updateTheme(@RequestBody @Valid UpdateThemeDTO dto) {
        profileService.updateTheme(UserContext.getUserId(), dto.getThemeKey());
        return ApiResult.ok();
    }
}
