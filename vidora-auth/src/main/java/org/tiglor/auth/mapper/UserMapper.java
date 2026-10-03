package org.tiglor.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.tiglor.auth.entity.User;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper extends BaseMapper<User> {
}
