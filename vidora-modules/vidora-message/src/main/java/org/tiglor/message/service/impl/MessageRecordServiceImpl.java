package org.tiglor.message.service.impl;

import org.tiglor.message.entity.MessageRecord;
import org.tiglor.message.mapper.MessageRecordMapper;
import org.tiglor.message.service.MessageRecordService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class MessageRecordServiceImpl extends ServiceImpl<MessageRecordMapper, MessageRecord> implements MessageRecordService {
}
