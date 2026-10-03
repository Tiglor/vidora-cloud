package org.tiglor.auth.controller;

import org.tiglor.auth.dto.UserLoginDTO;
import org.tiglor.auth.dto.UserRegisterDTO;
import org.tiglor.auth.service.AuthService;
import org.tiglor.auth.vo.LoginVO;
import org.tiglor.common.core.ApiResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ApiResult<LoginVO> login(@RequestBody @Valid UserLoginDTO dto) {
        return ApiResult.ok(authService.login(dto));
    }

    @PostMapping("/register")
    public ApiResult<Long> register(@RequestBody @Valid UserRegisterDTO dto) {
        return ApiResult.ok(authService.register(dto));
    }
}
