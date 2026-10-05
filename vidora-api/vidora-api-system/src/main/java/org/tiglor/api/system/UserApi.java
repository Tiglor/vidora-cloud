package org.tiglor.api.system;

import org.tiglor.api.system.dto.RemoteUserDTO;
import org.tiglor.common.core.ApiResult;

/**
 * system-service 对外发布的调用契约，由服务方拥有；调用方依赖本 jar 而不是手抄字段。
 * <p>
 * 契约里不写 {@code @GetMapping} 之类传输层注解，HTTP 侧由调用方的 Feign 接口
 * {@code extends} 本接口补齐。将来接 Dubbo provider 时直接 {@code implements} 同一份接口，
 * 届时的返回值应换成裸 DTO + 异常传播（{@code ApiResult} 是 HTTP 外壳，见 docs/ARCHITECTURE.md 6.2.1）。
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
