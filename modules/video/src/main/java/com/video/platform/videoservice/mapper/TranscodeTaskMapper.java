package com.video.platform.videoservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.video.platform.videoservice.entity.TranscodeTask;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TranscodeTaskMapper extends BaseMapper<TranscodeTask> {
}
