package org.tiglor.api.system;

import org.tiglor.api.system.dto.RemoteLoginUserDTO;
import org.tiglor.api.system.dto.RemoteRegisterDTO;

/**
 * system-service 面向 auth-service 的用户契约，由 system-service 拥有并发布。
 * <p>
 * 与 {@link UserApi} 的分工：{@code UserApi} 是 HTTP 出口（返回 {@code ApiResult} 外壳，
 * 由调用方的 Feign 接口 {@code extends} 后补 {@code @GetMapping}），本接口是 Dubbo 出口，
 * 返回裸 DTO。两条出口共存是因为它们的服务对象不同——一个给「查投稿用户昵称」这类跨服务读，
 * 一个给「登录链路要一次拿全」这类内网调用（判据见 .code/ARCHITECTURE.md 6.2.1）。
 * <p>
 * <b>本接口刻意不抛业务异常。</b> Dubbo 的 {@code ExceptionFilter} 只有在「异常类与接口类来自
 * 同一个 jar」或「异常出现在方法签名的 {@code throws} 上」时才原样传播；{@code BizException}
 * 住在 {@code common-core}，两个条件都不满足，会被换成只带堆栈字符串的 {@code RuntimeException}，
 * {@code code} 直接丢掉。所以「查不到」一律用 {@code null} 表达，由调用方决定怎么翻译成 HTTP 语义。
 */
public interface RemoteUserApi {

    /**
     * 按手机号取登录所需的全部信息（含密码哈希、角色码、权限码）。
     *
     * @return 手机号不存在时返回 {@code null}；调用方必须自己判空，
     *         并且不要把「不存在」和「密码错」区分开返回（否则等于开了手机号枚举）
     */
    RemoteLoginUserDTO getLoginUser(String phone);

    /**
     * 建号并授予默认角色，两步在 provider 侧同一个事务里。
     *
     * @return 新用户 id；手机号已被占用时返回 {@code null}
     *         （含唯一索引撞车的并发情况，不靠调用方先查一次来避免竞态）
     */
    Long register(RemoteRegisterDTO register);

    /**
     * 改用户自选主题。只更新这一列，不接受整实体入参——
     * 否则哪天 DTO 加了字段，这里就成了「用户能改自己任意资料」的越权口子。
     */
    void updateTheme(Long userId, String themeKey);
}
