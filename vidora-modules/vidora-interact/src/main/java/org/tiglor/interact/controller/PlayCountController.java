package org.tiglor.interact.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.tiglor.common.core.ApiResult;
import org.tiglor.common.core.security.UserContext;
import org.tiglor.interact.dto.VideoTotals;
import org.tiglor.interact.service.PlayCountService;

/**
 * 视频计数的上报与查询。
 * <p>
 * 播放器起播时调一次 POST，详情页调 GET 拿总数。
 * 两者都只要登录，不挂权限位。
 * </p>
 */
@RestController
@RequestMapping("/play-counts")
@RequiredArgsConstructor
public class PlayCountController {

    private final PlayCountService playCountService;

    /** 上报一次播放；返回 false 表示落在去重窗口内，本次未计数 */
    @PostMapping("/{videoId}")
    public ApiResult<Boolean> reportPlay(@PathVariable Long videoId) {
        return ApiResult.ok(playCountService.reportPlay(videoId, UserContext.getUserId()));
    }

    /** 累计计数（准实时，最多有 60 秒缓存延迟） */
    @GetMapping("/{videoId}")
    public ApiResult<VideoTotals> totals(@PathVariable Long videoId) {
        return ApiResult.ok(playCountService.totals(videoId));
    }
}
