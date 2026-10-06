package org.tiglor.content.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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
import org.tiglor.common.log.annotation.BusinessType;
import org.tiglor.common.log.annotation.OperLog;
import org.tiglor.content.dto.MachineAuditRequest;
import org.tiglor.content.dto.ManualReviewRequest;
import org.tiglor.content.entity.SecurityAudit;
import org.tiglor.content.service.SecurityAuditService;

/**
 * 内容安全审核结果。
 * <p>
 * 全部接口都要 {@code content:audit:manage}，包括读：审核结果里有原始机审判定
 * （哪个模型、多少分），对普通用户既没用也不该公开。
 * </p>
 */
@RestController
@RequestMapping("/security-audits")
@RequiredArgsConstructor
public class SecurityAuditController {

    private final SecurityAuditService service;

    /**
     * 回报机审结果
     *
     * <p>内容服务（video / interact）在机审跑完后调用。</p>
     */
    @PostMapping("/machine")
    @PreAuthorize("hasAuthority('content:audit:manage')")
    public ApiResult<Void> reportMachine(@Valid @RequestBody MachineAuditRequest request) {
        service.reportMachine(request);
        return ApiResult.ok();
    }

    /**
     * 查询对象审核结果
     *
     * <p>按 {@code targetType + targetId} 查，没审过时 data 为 null。</p>
     */
    @GetMapping("/target")
    @PreAuthorize("hasAuthority('content:audit:manage')")
    public ApiResult<SecurityAudit> findByTarget(@RequestParam String targetType, @RequestParam Long targetId) {
        return ApiResult.ok(service.findByTarget(targetType, targetId));
    }

    /**
     * 查询待复核队列
     *
     * <p>中高风险且未复核的记录，风险高的在前。</p>
     */
    @GetMapping("/pending")
    @PreAuthorize("hasAuthority('content:audit:manage')")
    public ApiResult<Page<SecurityAudit>> pending(@RequestParam(defaultValue = "1") long current,
                                                  @RequestParam(defaultValue = "20") long size) {
        return ApiResult.ok(service.pending(current, size));
    }

    /**
     * 审核记录分页
     *
     * <p>全量列表，最新一条在前。</p>
     * <p>
     * 和 {@code /pending} 的分工：那边只捞未复核的中高风险、按风险高低排；这边不按是否复核收窄，
     * 给运营按对象或等级回查具体某条内容当初的机审判定。
     * </p>
     *
     * @param targetType video / comment / danmaku，传其他值直接报参数错误而不是查出一页空结果
     * @param riskLevel  0-无 / 1-低 / 2-中 / 3-高；同样先验取值
     * @param reviewed   true 只看已人工复核过的，false 只看还没复核的，不传则两种都列
     */
    @GetMapping("/page")
    @PreAuthorize("hasAuthority('content:audit:manage')")
    public ApiResult<Page<SecurityAudit>> page(@RequestParam(defaultValue = "1") long current,
                                               @RequestParam(defaultValue = "20") long size,
                                               @RequestParam(required = false) String targetType,
                                               @RequestParam(required = false) Integer riskLevel,
                                               @RequestParam(required = false) Boolean reviewed) {
        return ApiResult.ok(service.page(current, size, targetType, riskLevel, reviewed));
    }

    /**
     * 提交人工复核
     *
     * <p>允许对已复核过的对象改判。</p>
     */
    @PutMapping("/{id}/review")
    @PreAuthorize("hasAuthority('content:audit:manage')")
    @OperLog(title = "内容审核", type = BusinessType.AUDIT)
    public ApiResult<SecurityAudit> review(@PathVariable Long id,
                                           @Valid @RequestBody ManualReviewRequest request) {
        return ApiResult.ok(service.review(id, request));
    }
}
