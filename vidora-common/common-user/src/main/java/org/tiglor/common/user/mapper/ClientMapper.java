package org.tiglor.common.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.tiglor.common.user.entity.Client;

@Mapper
public interface ClientMapper extends BaseMapper<Client> {
}
