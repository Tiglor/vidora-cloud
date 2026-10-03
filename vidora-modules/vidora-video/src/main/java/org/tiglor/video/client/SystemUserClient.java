package org.tiglor.video.client;

import org.tiglor.common.core.ApiResult;
import org.tiglor.video.client.dto.RemoteUserDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/** 视频服务调用系统服务的客户端示例。服务地址由 Nacos 的 system-service 解析。 */
@FeignClient(name = "system-service", configuration = FeignSecurityConfiguration.class)
public interface SystemUserClient {

    @GetMapping("/users/{id}")
    ApiResult<RemoteUserDTO> getById(@PathVariable("id") Long id);
}
