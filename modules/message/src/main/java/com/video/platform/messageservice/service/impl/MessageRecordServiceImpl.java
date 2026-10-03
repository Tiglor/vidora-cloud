package com.video.platform.messageservice.service.impl;

import com.video.platform.messageservice.entity.MessageRecord;
import com.video.platform.messageservice.mapper.MessageRecordMapper;
import com.video.platform.messageservice.service.MessageRecordService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class MessageRecordServiceImpl extends ServiceImpl<MessageRecordMapper, MessageRecord> implements MessageRecordService {
}
