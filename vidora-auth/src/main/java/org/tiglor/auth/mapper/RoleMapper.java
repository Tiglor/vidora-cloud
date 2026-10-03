package org.tiglor.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.tiglor.auth.entity.Role;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface RoleMapper extends BaseMapper<Role> {
}
