package org.tiglor.content.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.tiglor.content.entity.SecurityAudit;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

@Mapper
public interface SecurityAuditMapper extends BaseMapper<SecurityAudit> {

    /**
     * 写入或覆盖一条机审结果，按 {@code uk_target(target_type, target_id)} 定位。
     * <p>
     * 命中已有行时把 {@code manual_result} 重置为 NULL：人工当初放行的是**那一版**评分，
     * 机审复跑之后分数变了，旧结论不该继续生效——否则一条被重新判定为高风险的内容
     * 会因为几周前的一次人工放行而一直畅通。
     * </p>
     * <p>
     * 返回值同 {@code ON DUPLICATE KEY UPDATE} 的约定：1-插入 2-更新 0-没变，不是行数。
     * </p>
     */
    @Insert("""
            INSERT INTO content_security_audit
                (target_type, target_id, risk_level, risk_label, machine_result, manual_result)
            VALUES
                (#{targetType}, #{targetId}, #{riskLevel}, #{riskLabel}, #{machineResult}, NULL)
            ON DUPLICATE KEY UPDATE
                risk_level     = VALUES(risk_level),
                risk_label     = VALUES(risk_label),
                machine_result = VALUES(machine_result),
                manual_result  = NULL
            """)
    int upsertMachineResult(@Param("targetType") String targetType,
                            @Param("targetId") Long targetId,
                            @Param("riskLevel") int riskLevel,
                            @Param("riskLabel") String riskLabel,
                            @Param("machineResult") String machineResult);
}
