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

    @PutMapping("/theme")
    public ApiResult<Void> updateTheme(@RequestBody @Valid UpdateThemeDTO dto) {
        profileService.updateTheme(UserContext.getUserId(), dto.getThemeKey());
        return ApiResult.ok();
    }
}
