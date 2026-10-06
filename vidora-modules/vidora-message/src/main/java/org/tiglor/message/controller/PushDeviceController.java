package org.tiglor.message.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.tiglor.common.core.ApiResult;
import org.tiglor.common.core.security.UserContext;
import org.tiglor.message.dto.PushDeviceRequest;
import org.tiglor.message.entity.PushDevice;
import org.tiglor.message.service.PushDeviceService;

import java.util.List;

/**
 * 推送设备绑定。
 * <p>
 * 客户端在拿到厂商 token 后（启动、登录、token 刷新）调 {@code POST}，退出登录时调 {@code DELETE}。
 * 只能操作自己的绑定，userId 一律取自登录上下文。
 * </p>
 */
@RestController
@RequestMapping("/push-devices")
@RequiredArgsConstructor
public class PushDeviceController {

    private final PushDeviceService pushDeviceService;

    /**
     * 查询我的推送通道
     *
     * <p>只给当前有效的通道。一个用户最多 3 种 deviceType × 4 种 vendor，不分页。</p>
     */
    @GetMapping
    public ApiResult<List<PushDevice>> listMine() {
        return ApiResult.ok(pushDeviceService.listMine(UserContext.getUserId()));
    }

    /**
     * 绑定推送通道
     *
     * <p>已存在的通道换 token 并重新置为有效；同一条 token 在别的用户名下的绑定会同时失效。</p>
     */
    @PostMapping
    public ApiResult<PushDevice> bind(@Valid @RequestBody PushDeviceRequest request) {
        return ApiResult.ok(pushDeviceService.bind(request, UserContext.getUserId()));
    }

    /**
     * 解绑推送通道
     *
     * <p>返回被解绑的数量。</p>
     *
     * @param deviceType 与 {@code vendor} 都省略表示解绑全部通道
     */
    @DeleteMapping
    public ApiResult<Integer> unbind(@RequestParam(required = false) String deviceType,
                                     @RequestParam(required = false) String vendor) {
        return ApiResult.ok(pushDeviceService.unbind(UserContext.getUserId(), deviceType, vendor));
    }
}
