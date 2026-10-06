package org.tiglor.api.system;

import org.tiglor.api.system.dto.RemoteUserDTO;
import org.tiglor.common.core.ApiResult;

/**
 * system-service 对外发布的 <b>HTTP</b> 调用契约，由服务方拥有；调用方依赖本 jar 而不是手抄字段。
 * <p>
 * 契约里不写 {@code @GetMapping} 之类传输层注解，HTTP 侧由调用方的 Feign 接口
 * {@code extends} 本接口补齐（{@code @Override} 保证签名不漂移）。
 * <p>
 * Dubbo 出口没有复用本接口，而是另开了 {@link RemoteUserApi} / {@link RemoteClientApi}：
 * {@code ApiResult} 是 HTTP 的响应外壳，服务间调用不该被强制套上它，所以那边的返回类型是裸 DTO。
 * 两套出口的归属判据见 .code/ARCHITECTURE.md 6.2.1。
 */
public interface UserApi {

    /**
     * 按 id 取投稿用户的最小视图。
     * <p>
     * 用户不存在时 provider 侧返回的是 {@code code:200 + data:null}（{@code UserController.getById}
     * 走 MP 的 {@code getById}，没有 404 语义），调用方必须自己判空。
     */
    ApiResult<RemoteUserDTO> getById(Long id);
}
