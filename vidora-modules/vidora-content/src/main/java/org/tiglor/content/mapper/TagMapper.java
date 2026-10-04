package org.tiglor.content.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.tiglor.content.entity.Tag;

/**
 * 标签读写。全部走 MyBatis-Plus 的条件构造器，没有需要手写的 SQL：
 * 热门排序是 {@code status = 1 ORDER BY use_count DESC}，前缀联想是 {@code name LIKE 'kw%'}，
 * 两者都能命中 {@code idx_status_count} / {@code uk_name}。
 */
@Mapper
public interface TagMapper extends BaseMapper<Tag> {
}
