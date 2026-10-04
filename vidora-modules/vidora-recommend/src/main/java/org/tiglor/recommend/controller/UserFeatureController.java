package org.tiglor.recommend.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.tiglor.common.core.ApiResult;
import org.tiglor.recommend.dto.UserFeatureBatchRequest;
import org.tiglor.recommend.entity.UserFeature;
import org.tiglor.recommend.service.UserFeatureService;

import java.util.List;

/**
 * 用户行为特征。
 * <p>
 * 全部接口都要权限，包括读。特征是「这个人喜欢什么」的画像，比推荐结果更敏感，
 * 而且读写双方都是算法任务和管理端，没有终端用户的用法——
 * 所以这里不提供 {@code /mine} 之类的自助接口，userId 一律是显式参数。
 * </p>
 * <p>
 * 读和写用 {@code recommend:manage}，清空画像用 {@code recommend:purge}：
 * 后者不可逆，不该和特征任务的日常写入放在同一个权限位上。
 * </p>
 */
@RestController
@RequestMapping("/user-features")
@RequiredArgsConstructor
public class UserFeatureController {

    private final UserFeatureService service;

    /** 某用户某一类特征里权重最高的若干个，给召回侧读 */
    @GetMapping("/top")
    @PreAuthorize("hasAuthority('recommend:manage')")
    public ApiResult<List<UserFeature>> top(@RequestParam Long userId,
                                            @RequestParam String featureType,
                                            @RequestParam(defaultValue = "50") int limit) {
        return ApiResult.ok(service.top(userId, featureType, limit));
    }

    /** 特征任务写入一个用户的画像，同 (类型, 值) 覆盖权重而不是累加 */
    @PostMapping("/batch")
    @PreAuthorize("hasAuthority('recommend:manage')")
    public ApiResult<Integer> batchUpsert(@Valid @RequestBody UserFeatureBatchRequest request) {
        return ApiResult.ok(service.batchUpsert(request));
    }

    /**
     * 删掉某用户的特征，{@code featureType} 不传就是清空这个人的全部画像。
     *
     * @return 删除行数
     */
    @DeleteMapping
    @PreAuthorize("hasAuthority('recommend:purge')")
    public ApiResult<Integer> removeFeatures(@RequestParam Long userId,
                                             @RequestParam(required = false) String featureType) {
        return ApiResult.ok(service.removeFeatures(userId, featureType));
    }
}
