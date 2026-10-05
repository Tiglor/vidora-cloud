package org.tiglor.common.log.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.tiglor.common.log.entity.OperLogEntity;

/**
 * 操作审计表 Mapper。
 * <p>
 * 放在 common-log 里，由要读写它的那个服务通过 {@code @MapperScan} 认领
 * （目前只有 system-service）—— 生产端只上报、不直连库，跨库写是分布式系统里最贵的一种方便。
 */
public interface OperLogMapper extends BaseMapper<OperLogEntity> {
}
