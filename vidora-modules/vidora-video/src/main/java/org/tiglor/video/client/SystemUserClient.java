package org.tiglor.video.client;

import org.tiglor.api.system.UserApi;
import org.tiglor.api.system.dto.RemoteUserDTO;
import org.tiglor.common.core.ApiResult;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * 视频服务调用系统服务的客户端。服务地址由 Nacos 的 system-service 解析。
 * <p>
 * 契约住在 {@link UserApi}（由 system-service 拥有），本接口只补 HTTP 侧的东西：
 * 契约模块刻意不引 spring-web，所以 {@code @GetMapping} 只能落在这里，
 * 靠 {@code @Override} 保证方法签名与返回类型不会和契约漂移。
 */
@FeignClient(name = "system-service", configuration = FeignSecurityConfiguration.class)
public interface SystemUserClient extends UserApi {

    @Override
    @GetMapping("/users/{id}")
    ApiResult<RemoteUserDTO> getById(@PathVariable("id") Long id);
}
