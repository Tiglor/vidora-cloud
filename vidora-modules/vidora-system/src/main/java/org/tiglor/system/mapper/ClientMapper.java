package org.tiglor.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.tiglor.system.entity.Client;

@Mapper
public interface ClientMapper extends BaseMapper<Client> {
}
