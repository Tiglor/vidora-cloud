package com.video.platform.authservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.video.platform.authservice.entity.User;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper extends BaseMapper<User> {
}
