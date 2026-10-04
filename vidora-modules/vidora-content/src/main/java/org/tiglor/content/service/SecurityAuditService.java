package org.tiglor.content.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.IService;
import org.tiglor.content.dto.MachineAuditRequest;
import org.tiglor.content.dto.ManualReviewRequest;
import org.tiglor.content.entity.SecurityAudit;

public interface SecurityAuditService extends IService<SecurityAudit> {

    /** 写入或覆盖一条机审结果，同时把已有的人工结论作废 */
    void reportMachine(MachineAuditRequest request);

    /**
     * 查一个对象的审核结果。
     *
     * @return 还没审过时返回 null——「没有记录」是正常状态，不是错误
     */
    SecurityAudit findByTarget(String targetType, Long targetId);

    /** 待人工复核的队列：中高风险且还没人复核过，风险高的在前、同风险按提交先后 */
    Page<SecurityAudit> pending(long current, long size);

    /** 管理端分页，三个过滤条件都可选；reviewed 为 null 表示不过滤 */
    Page<SecurityAudit> page(long current, long size, String targetType, Integer riskLevel, Boolean reviewed);

    /** 人工复核。允许改判：复核结论本身也可能错 */
    SecurityAudit review(Long id, ManualReviewRequest request);
}
