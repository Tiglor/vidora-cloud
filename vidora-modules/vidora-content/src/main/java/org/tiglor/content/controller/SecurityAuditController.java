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

    /** 内容服务（video / interact）在机审跑完后回报结果 */
    @PostMapping("/machine")
    @PreAuthorize("hasAuthority('content:audit:manage')")
    public ApiResult<Void> reportMachine(@Valid @RequestBody MachineAuditRequest request) {
        service.reportMachine(request);
        return ApiResult.ok();
    }

    /** 查单个对象的审核结果，没审过时 data 为 null */
    @GetMapping("/target")
    @PreAuthorize("hasAuthority('content:audit:manage')")
    public ApiResult<SecurityAudit> findByTarget(@RequestParam String targetType, @RequestParam Long targetId) {
        return ApiResult.ok(service.findByTarget(targetType, targetId));
    }

    /** 待人工复核队列：中高风险且未复核，风险高的在前 */
    @GetMapping("/pending")
    @PreAuthorize("hasAuthority('content:audit:manage')")
    public ApiResult<Page<SecurityAudit>> pending(@RequestParam(defaultValue = "1") long current,
                                                  @RequestParam(defaultValue = "20") long size) {
        return ApiResult.ok(service.pending(current, size));
    }

    @GetMapping("/page")
    @PreAuthorize("hasAuthority('content:audit:manage')")
    public ApiResult<Page<SecurityAudit>> page(@RequestParam(defaultValue = "1") long current,
                                               @RequestParam(defaultValue = "20") long size,
                                               @RequestParam(required = false) String targetType,
                                               @RequestParam(required = false) Integer riskLevel,
                                               @RequestParam(required = false) Boolean reviewed) {
        return ApiResult.ok(service.page(current, size, targetType, riskLevel, reviewed));
    }

    /** 人工复核，允许对已复核过的对象改判 */
    @PutMapping("/{id}/review")
    @PreAuthorize("hasAuthority('content:audit:manage')")
    @OperLog(title = "内容审核", type = BusinessType.AUDIT)
    public ApiResult<SecurityAudit> review(@PathVariable Long id,
                                           @Valid @RequestBody ManualReviewRequest request) {
        return ApiResult.ok(service.review(id, request));
    }
}
