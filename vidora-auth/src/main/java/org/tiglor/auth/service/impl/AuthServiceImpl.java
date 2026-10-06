package org.tiglor.auth.service.impl;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.tiglor.api.system.RemoteClientApi;
import org.tiglor.api.system.RemoteUserApi;
import org.tiglor.api.system.dto.RemoteClientDTO;
import org.tiglor.api.system.dto.RemoteLoginUserDTO;
import org.tiglor.api.system.dto.RemoteRegisterDTO;
import org.tiglor.auth.dto.UserLoginDTO;
import org.tiglor.auth.dto.UserRegisterDTO;
import org.tiglor.auth.service.AuthService;
import org.tiglor.auth.vo.LoginVO;
import org.tiglor.common.core.BizException;
import org.tiglor.common.core.JwtUtil;
import org.tiglor.common.core.ResultCode;

/**
 * 登录与注册。
 * <p>
 * 本服务不连库：用户、角色、权限、客户端全部经 Dubbo 向 system-service 取。
 * 留在这一侧的只有两件认证语义的事——密码比对与「失败提示不许区分账号不存在和密码错」，
 * 前者要 {@link PasswordEncoder}，后者是防手机号枚举的策略，都属于认证服务而不是用户库。
 */
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    /**
     * Dubbo 的引用注入走 BeanPostProcessor，只能打在非 final 字段上，
     * 所以这两个不在 {@code @RequiredArgsConstructor} 生成的构造器里。
     */
    @DubboReference
    private RemoteUserApi remoteUserApi;

    @DubboReference
    private RemoteClientApi remoteClientApi;

    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    @Override
    public LoginVO login(UserLoginDTO dto) {
        RemoteLoginUserDTO user = remoteUserApi.getLoginUser(dto.getPhone());
        // 账号不存在与密码错误返回同一提示，避免手机号枚举。
        // user 为 null 时短路，不会拿 null 哈希去调 matches
        if (user == null || !passwordEncoder.matches(dto.getPassword(), user.getPasswordHash())) {
            throw new BizException(ResultCode.UNAUTHORIZED, "手机号或密码错误");
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new BizException(ResultCode.FORBIDDEN, "账号已被禁用");
        }

        // 校验客户端
        RemoteClientDTO client = remoteClientApi.getByClientId(dto.getClientId());
        if (client == null || client.getStatus() == null || client.getStatus() != 1) {
            throw new BizException(ResultCode.VALIDATE_FAILED, "客户端未注册或已停用");
        }
        if (client.getGrantType() == null || !client.getGrantType().contains("password")) {
            throw new BizException(ResultCode.VALIDATE_FAILED, "该客户端不支持密码登录");
        }

        List<String> roles = orEmpty(user.getRoles());
        List<String> permissions = orEmpty(user.getPermissions());
        String token = jwtUtil.generateToken(
                user.getId(), String.join(",", roles), String.join(",", permissions),
                client.getClientId(), client.getClientKey(), client.getTimeout());

        LoginVO vo = new LoginVO();
        vo.setToken(token);
        vo.setUserId(user.getId());
        vo.setNickname(user.getNickname());
        vo.setAvatarUrl(user.getAvatarUrl());
        vo.setRoles(roles);
        vo.setPermissions(permissions);
        vo.setClientId(client.getClientId());
        vo.setClientKey(client.getClientKey());
        vo.setExpiresIn((long) client.getTimeout());
        // 主题随登录一起回来：省掉一次「登录后再拉偏好」的请求，也避免首屏先闪一下默认色
        vo.setThemeKey(user.getThemeKey());
        return vo;
    }

    @Override
    public Long register(UserRegisterDTO dto) {
        RemoteRegisterDTO register = new RemoteRegisterDTO();
        register.setPhone(dto.getPhone());
        register.setNickname(dto.getNickname());
        // BCrypt 编码留在本服务：明文密码不该出现在任何一条服务间链路上，
        // 哪怕是内网 RPC——对端的访问日志、线程转储、异常堆栈都会把它带出去
        register.setPasswordHash(passwordEncoder.encode(dto.getPassword()));

        // 事务在 provider 侧（建号 + 授默认角色两步同一个事务），这里不再标 @Transactional：
        // 本地已经没有数据库，标了也只是开个空事务
        Long userId = remoteUserApi.register(register);
        if (userId == null) {
            throw new BizException(ResultCode.VALIDATE_FAILED, "手机号已注册");
        }
        return userId;
    }

    /** 契约约定给空列表，但反序列化出来的东西不值得无条件相信，join 之前兜一次 */
    private static List<String> orEmpty(List<String> value) {
        return value == null ? List.of() : value;
    }
}
