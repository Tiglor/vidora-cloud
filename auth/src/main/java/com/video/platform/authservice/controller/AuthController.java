package com.video.platform.authservice.controller;

import com.video.platform.authservice.dto.UserLoginDTO;
import com.video.platform.authservice.dto.UserRegisterDTO;
import com.video.platform.authservice.service.AuthService;
import com.video.platform.authservice.vo.LoginVO;
import com.video.platform.common.ApiResult;
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
