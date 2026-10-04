package org.tiglor.content.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.tiglor.common.core.BizException;
import org.tiglor.common.core.ResultCode;
import org.tiglor.common.core.support.JsonValues;
import org.tiglor.content.dto.MachineAuditRequest;
import org.tiglor.content.dto.ManualReviewRequest;
import org.tiglor.content.entity.SecurityAudit;
import org.tiglor.content.enums.AuditTargetType;
import org.tiglor.content.enums.ManualResult;
import org.tiglor.content.enums.RiskLevel;
import org.tiglor.content.mapper.SecurityAuditMapper;
import org.tiglor.content.service.SecurityAuditService;

/**
 * 内容安全审核。
 * <p>
 * 一个对象一行（{@code uk_target}），机审复跑覆盖旧结果并把人工结论作废——
 * 人工当初放行的是那一版评分，分数变了旧结论就不该继续生效。
 * </p>
 */
@Service
public class SecurityAuditServiceImpl extends ServiceImpl<SecurityAuditMapper, SecurityAudit>
        implements SecurityAuditService {

    private static final long MAX_PAGE_SIZE = 100L;

    @Override
    public void reportMachine(MachineAuditRequest request) {
        AuditTargetType type = AuditTargetType.of(request.getTargetType());
        RiskLevel level = RiskLevel.of(request.getRiskLevel());
        if (request.getTargetId() == null || request.getTargetId() <= 0) {
            throw new BizException(ResultCode.VALIDATE_FAILED, "targetId 非法");
        }
        baseMapper.upsertMachineResult(type.getCode(), request.getTargetId(), level.getCode(),
                trimToNull(request.getRiskLabel()),
                // machine_result 是 JSON 列，非法 JSON 会被 MySQL 拒掉并抛 3140，
                // 那个错误传到调用方就只剩一句「服务异常」，所以先在这里验一遍
                JsonValues.requireValid(request.getMachineResult(), "machineResult"));
    }

    @Override
    public SecurityAudit findByTarget(String targetType, Long targetId) {
        AuditTargetType type = AuditTargetType.of(targetType);
        if (targetId == null || targetId <= 0) {
            throw new BizException(ResultCode.VALIDATE_FAILED, "targetId 非法");
        }
        return lambdaQuery()
                .eq(SecurityAudit::getTargetType, type.getCode())
                .eq(SecurityAudit::getTargetId, targetId)
                .one();
    }

    @Override
    public Page<SecurityAudit> pending(long current, long size) {
        return lambdaQuery()
                .isNull(SecurityAudit::getManualResult)
                .ge(SecurityAudit::getRiskLevel, RiskLevel.MEDIUM.getCode())
                .orderByDesc(SecurityAudit::getRiskLevel)
                // 同一风险等级内先来先审，否则排在队尾的永远轮不到
                .orderByAsc(SecurityAudit::getId)
                .page(new Page<>(Math.max(current, 1), clampSize(size)));
    }

    @Override
    public Page<SecurityAudit> page(long current, long size, String targetType, Integer riskLevel, Boolean reviewed) {
        AuditTargetType type = targetType == null || targetType.isBlank() ? null : AuditTargetType.of(targetType);
        if (riskLevel != null) {
            // 只为校验取值：传一个 7 进来应该当场报 400，而不是安静地查出一页空结果
            RiskLevel.of(riskLevel);
        }
        return lambdaQuery()
                .eq(type != null, SecurityAudit::getTargetType, type == null ? null : type.getCode())
                .eq(riskLevel != null, SecurityAudit::getRiskLevel, riskLevel)
                .isNotNull(Boolean.TRUE.equals(reviewed), SecurityAudit::getManualResult)
                .isNull(Boolean.FALSE.equals(reviewed), SecurityAudit::getManualResult)
                .orderByDesc(SecurityAudit::getId)
                .page(new Page<>(Math.max(current, 1), clampSize(size)));
    }

    @Override
    public SecurityAudit review(Long id, ManualReviewRequest request) {
        ManualResult result = ManualResult.of(request.getManualResult());
        if (getById(id) == null) {
            throw new BizException(ResultCode.NOT_FOUND, "审核记录不存在：" + id);
        }
        String label = trimToNull(request.getRiskLabel());
        lambdaUpdate()
                .set(SecurityAudit::getManualResult, result.getCode())
                // 标签不传就保留机审给的那个
                .set(label != null, SecurityAudit::getRiskLabel, label)
                .eq(SecurityAudit::getId, id)
                .update();
        return getById(id);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static long clampSize(long size) {
        return Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
    }
}
