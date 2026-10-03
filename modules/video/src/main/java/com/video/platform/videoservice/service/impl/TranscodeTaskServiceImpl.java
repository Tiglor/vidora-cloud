package com.video.platform.videoservice.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.video.platform.videoservice.entity.TranscodeTask;
import com.video.platform.videoservice.mapper.TranscodeTaskMapper;
import com.video.platform.videoservice.service.TranscodeTaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TranscodeTaskServiceImpl extends ServiceImpl<TranscodeTaskMapper, TranscodeTask> implements TranscodeTaskService {
}
