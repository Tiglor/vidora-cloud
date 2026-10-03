package com.video.platform.recommendservice.service.impl;

import com.video.platform.recommendservice.entity.RecommendResult;
import com.video.platform.recommendservice.mapper.RecommendResultMapper;
import com.video.platform.recommendservice.service.RecommendResultService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class RecommendResultServiceImpl extends ServiceImpl<RecommendResultMapper, RecommendResult> implements RecommendResultService {
}
