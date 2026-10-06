package org.tiglor.auth.controller;

import org.tiglor.auth.dto.UserLoginDTO;
import org.tiglor.auth.dto.UserRegisterDTO;
import org.tiglor.auth.service.AuthService;
import org.tiglor.auth.support.LoginAuditor;
import org.tiglor.auth.vo.LoginVO;
import org.tiglor.common.core.ApiResult;
import org.tiglor.common.core.BizException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证入口：手机号密码登录与注册。
 * <p>
 * 这两个接口在网关白名单里，不带 Token 就能调；换出去的 JWT 再由网关校验并把 userId / clientId
 * 透传给下游服务。本服务不连数据库——用户、角色、权限、客户端配置一律经 Dubbo 向 system-service 取，
 * 留在这一侧的只有密码比对和发 Token。
 * </p>
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final LoginAuditor loginAuditor;

    /**
     * 账号登录
     *
     * <p>成功与失败都要落登录日志，所以异常原样抛之前先记一条。</p>
     * <p>
     * 记在控制器而不是 service：失败原因在这里就是抛出的那条 BizException 消息，
     * 而且 request（取 IP、UA）在这一层是现成的，不用把 HttpServletRequest 拖进 service。
     * </p>
     */
    @PostMapping("/login")
    public ApiResult<LoginVO> login(@RequestBody @Valid UserLoginDTO dto, HttpServletRequest request) {
        try {
            LoginVO vo = authService.login(dto);
            loginAuditor.success(dto, vo, request);
            return ApiResult.ok(vo);
        } catch (BizException e) {
            loginAuditor.failure(dto, e.getMessage(), request);
            throw e;
        }
    }

    /**
     * 账号注册
     *
     * <p>注册不进登录日志：它不是「认证事件」，是账号创建，量级也和登录差一个数量级。</p>
     */
    @PostMapping("/register")
    public ApiResult<Long> register(@RequestBody @Valid UserRegisterDTO dto) {
        return ApiResult.ok(authService.register(dto));
    }
}
