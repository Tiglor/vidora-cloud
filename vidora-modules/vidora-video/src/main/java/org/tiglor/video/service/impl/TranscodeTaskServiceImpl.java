package org.tiglor.video.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import org.tiglor.video.entity.TranscodeTask;
import org.tiglor.video.mapper.TranscodeTaskMapper;
import org.tiglor.video.service.TranscodeTaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TranscodeTaskServiceImpl extends ServiceImpl<TranscodeTaskMapper, TranscodeTask> implements TranscodeTaskService {
}
