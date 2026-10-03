package org.tiglor.video.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.tiglor.video.entity.TranscodeTask;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TranscodeTaskMapper extends BaseMapper<TranscodeTask> {
}
