package org.tiglor.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.tiglor.common.log.entity.OperLogEntity;
import org.tiglor.common.log.mapper.OperLogMapper;
import org.tiglor.common.user.entity.User;
import org.tiglor.common.user.mapper.UserMapper;
import org.tiglor.system.service.OperLogService;
import org.tiglor.system.vo.OperLogVO;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OperLogServiceImpl extends ServiceImpl<OperLogMapper, OperLogEntity> implements OperLogService {

    /** 列表页跳过的两个大文本列，详情接口再单独取 */
    private static final Set<String> BIG_COLUMNS = Set.of("oper_param", "json_result");

    private final UserMapper userMapper;

    @Override
    public Page<OperLogVO> pageLogs(long current, long size, String title, Integer businessType,
                                   Long operUserId, Integer status, LocalDateTime beginTime, LocalDateTime endTime) {
        Page<OperLogEntity> raw = page(new Page<>(current, size), new LambdaQueryWrapper<OperLogEntity>()
                // 请求参数与返回值都是 2000 字符级别的大字段，列表页一行都别查，详情接口再单独取
                .select(OperLogEntity.class, field -> !BIG_COLUMNS.contains(field.getColumn()))
                .like(StringUtils.hasText(title), OperLogEntity::getTitle, title)
                .eq(businessType != null, OperLogEntity::getBusinessType, businessType)
                .eq(operUserId != null, OperLogEntity::getOperUserId, operUserId)
                .eq(status != null, OperLogEntity::getStatus, status)
                .ge(beginTime != null, OperLogEntity::getCreateTime, beginTime)
                .le(endTime != null, OperLogEntity::getCreateTime, endTime)
                .orderByDesc(OperLogEntity::getId));

        Page<OperLogVO> result = new Page<>(raw.getCurrent(), raw.getSize(), raw.getTotal());
        List<OperLogEntity> records = raw.getRecords();
        if (records.isEmpty()) {
            result.setRecords(List.of());
            return result;
        }
        Map<Long, String> nicknames = nicknames(records);
        result.setRecords(records.stream().map(entity -> {
            OperLogVO vo = new OperLogVO();
            BeanUtils.copyProperties(entity, vo);
            vo.setOperUserName(displayName(entity.getOperUserId(), nicknames));
            return vo;
        }).toList());
        return result;
    }

    @Override
    public OperLogVO detail(Long id) {
        OperLogEntity entity = getById(id);
        if (entity == null) {
            return null;
        }
        OperLogVO vo = new OperLogVO();
        BeanUtils.copyProperties(entity, vo);
        vo.setOperUserName(displayName(entity.getOperUserId(), nicknames(List.of(entity))));
        return vo;
    }

    /** 批量取昵称，一次 IN 查询，别在列表循环里逐条查库 */
    private Map<Long, String> nicknames(Collection<OperLogEntity> records) {
        Set<Long> userIds = records.stream()
                .map(OperLogEntity::getOperUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return userMapper.selectList(new LambdaQueryWrapper<User>()
                        .select(User::getId, User::getNickname)
                        .in(User::getId, userIds))
                .stream()
                .filter(u -> StringUtils.hasText(u.getNickname()))
                .collect(Collectors.toMap(User::getId, User::getNickname, (a, b) -> a));
    }

    /** 注销用户查不到昵称，也要留下一个能认人的兜底，不能显示成空白 */
    private String displayName(Long userId, Map<Long, String> nicknames) {
        if (userId == null) {
            return "系统";
        }
        return nicknames.getOrDefault(userId, "用户#" + userId);
    }
}
