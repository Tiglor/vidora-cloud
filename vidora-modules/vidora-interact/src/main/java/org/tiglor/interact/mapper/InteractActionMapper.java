package org.tiglor.interact.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.tiglor.interact.dto.ActionTypeCount;
import org.tiglor.interact.entity.InteractAction;

import java.util.List;

@Mapper
public interface InteractActionMapper extends BaseMapper<InteractAction> {

    /**
     * 一次查出某对象各动作的有效数量。
     * <p>
     * 走 idx_target_action(target_type, target_id, action_type, status)，
     * 四个字段全在索引里，是覆盖索引扫描，不用回表。分成三条 COUNT 查也行，
     * 但热门视频详情页每次打开都要查，省两次往返值得。
     * </p>
     */
    @Select("""
            SELECT action_type AS actionType, COUNT(*) AS total
            FROM interact_action
            WHERE target_type = #{targetType}
              AND target_id = #{targetId}
              AND status = 1
            GROUP BY action_type
            """)
    List<ActionTypeCount> countByTarget(@Param("targetType") String targetType,
                                        @Param("targetId") Long targetId);
}
