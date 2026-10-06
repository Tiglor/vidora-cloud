package org.tiglor.api.system;

import org.tiglor.api.system.dto.RemoteClientDTO;

/**
 * system-service 的客户端（{@code sys_client}）契约，由 system-service 拥有并发布。
 * <p>
 * 单独一个接口而不是塞进 {@link RemoteUserApi}：客户端是「哪个端在调」的注册表，
 * 和用户是两码事，管理端的客户端 CRUD 也走的是 system-service 自己的 HTTP 出口。
 * <p>
 * 不抛业务异常的理由同 {@link RemoteUserApi} 的类注释。
 */
public interface RemoteClientApi {

    /**
     * 按 clientId 取客户端配置。
     *
     * @return 未注册时返回 {@code null}；「已注册但停用」「不支持该授权类型」由调用方判，
     *         因为那两种情况要返回的错误提示属于认证语义
     */
    RemoteClientDTO getByClientId(String clientId);
}
