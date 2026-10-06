package org.tiglor.interact.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.tiglor.common.core.ApiResult;
import org.tiglor.common.core.security.UserContext;
import org.tiglor.interact.dto.DanmakuSendRequest;
import org.tiglor.interact.entity.Danmaku;
import org.tiglor.interact.service.DanmakuService;

import java.math.BigDecimal;
import java.util.List;

/** 弹幕收发。发送只要登录即可，屏蔽需要 {@code danmaku:manage}。 */
@RestController
@RequestMapping("/danmaku")
@RequiredArgsConstructor
public class DanmakuController {

    private final DanmakuService danmakuService;

    /**
     * 查询视频弹幕列表
     *
     * <p>拉取某视频的弹幕时间线</p>
     * <p>
     * 长视频请带 fromTime / toTime 分段拉：单次最多返回
     * {@link DanmakuService#MAX_PER_LOAD} 条，超出部分不会返回。
     * </p>
     */
    @GetMapping("/video/{videoId}")
    public ApiResult<List<Danmaku>> listByVideo(@PathVariable Long videoId,
                                                @RequestParam(required = false) BigDecimal fromTime,
                                                @RequestParam(required = false) BigDecimal toTime,
                                                @RequestParam(defaultValue = "3000") int limit) {
        return ApiResult.ok(danmakuService.listByVideo(videoId, fromTime, toTime, limit));
    }

    /**
     * 发送弹幕
     *
     * <p>发一条弹幕到某视频的时间线上，发送人取当前登录用户</p>
     * <p>
     * 样式字段可省略，服务端补默认值（白色 25px 滚动）；出现时间传「秒」，可带小数并按四舍五入存到 3 位精度，
     * 颜色会被统一转成大写十六进制。入库即正常显示，既不送审也不限流，所以不是幂等的：
     * 同一内容点两次「发送」就存两条，去重是客户端的事。
     * </p>
     *
     * @return 落库后的完整弹幕对象，含 id 与服务端补好的默认样式，可直接插入本地时间线渲染
     */
    @PostMapping
    public ApiResult<Danmaku> send(@Valid @RequestBody DanmakuSendRequest request) {
        return ApiResult.ok(danmakuService.send(request, UserContext.getUserId()));
    }

    /**
     * 屏蔽或恢复弹幕
     *
     * <p>屏蔽(0) / 恢复(1)</p>
     */
    @PutMapping("/{id}/status")
    @PreAuthorize("hasAuthority('danmaku:manage')")
    public ApiResult<Void> setStatus(@PathVariable Long id, @RequestParam int status) {
        danmakuService.setStatus(id, status);
        return ApiResult.ok();
    }
}
